package com.fsd.dispatch.geo;

import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Calculates route metrics (P1-5): length, ETA, waiting time, charging time, risk points.
 *
 * Used by the dispatch flow and admin API to surface trip-level information
 * beyond just the polyline. All values are best-effort estimates based on
 * road segment speed limits and station service times.
 */
@Component
public class RouteMetricsCalculator {

    private final RoadSegmentMapper roadSegmentMapper;
    private final RoadNodeMapper roadNodeMapper;

    public RouteMetricsCalculator(RoadSegmentMapper roadSegmentMapper, RoadNodeMapper roadNodeMapper) {
        this.roadSegmentMapper = roadSegmentMapper;
        this.roadNodeMapper = roadNodeMapper;
    }

    /**
     * Compute metrics for a planned route.
     *
     * @param parkId park ID for loading segment metadata
     * @param polyline route polyline (GCJ-02)
     * @param nodePath optional road node codes for risk-point detection
     * @param pickup pickup station (nullable = no service time at pickup)
     * @param dropoff dropoff station (nullable = no service time at dropoff)
     * @param chargingStop charging station if route includes charging (nullable = no charging)
     * @return RouteMetrics with travel/waiting/charging times and risk points
     */
    public RouteMetrics compute(Long parkId,
                                 List<ParkGeoTransformService.GeoPoint> polyline,
                                 List<String> nodePath,
                                 StationEntity pickup,
                                 StationEntity dropoff,
                                 StationEntity chargingStop) {
        if (polyline == null || polyline.size() < 2) {
            return RouteMetrics.empty();
        }

        // Total length: sum of haversine distances between consecutive points
        double totalMeters = 0;
        for (int i = 1; i < polyline.size(); i++) {
            totalMeters += GeoPolygonUtils.haversineMeters(polyline.get(i - 1), polyline.get(i));
        }

        // Travel time: 逐路段按各自限速累加，未覆盖的部分按实测回退速度（见 FALLBACK_SPEED_KMH）
        Map<String, RoadSegmentEntity> segmentByNodePair = loadSegmentIndex(parkId);
        long travelSeconds = estimateTravelSeconds(polyline, nodePath, segmentByNodePair, parkId);

        // Service times (waiting) at pickup/dropoff
        long waitingSeconds = 0L;
        if (pickup != null && pickup.getAvgServiceSeconds() != null) {
            waitingSeconds += pickup.getAvgServiceSeconds();
        }
        if (dropoff != null && dropoff.getAvgServiceSeconds() != null) {
            waitingSeconds += dropoff.getAvgServiceSeconds();
        }

        // Charging time (rough estimate based on max power and 0->80% SOC)
        long chargingSeconds = 0L;
        if (chargingStop != null) {
            // Default 30 min charging session if no pile power available
            chargingSeconds = Duration.ofMinutes(30).toSeconds();
        }

        // Risk points: nodes where access_state is RESTRICTED / NO_STOP / LOADING_ONLY /
        // CHARGING_ACCESS, or segments with gate_code set
        List<String> riskPoints = collectRiskPoints(parkId, nodePath);

        return new RouteMetrics(
                totalMeters,
                travelSeconds,
                waitingSeconds,
                chargingSeconds,
                riskPoints,
                null,
                null);
    }

    private Map<String, RoadSegmentEntity> loadSegmentIndex(Long parkId) {
        if (parkId == null) {
            return Map.of();
        }
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                .eq("park_id", parkId)
                .eq("deleted", 0));
        Map<String, RoadSegmentEntity> index = new HashMap<>();
        for (RoadSegmentEntity seg : segments) {
            index.put(directedKey(seg.getFromNodeCode(), seg.getToNodeCode()), seg);
            // Bidirectional default — also index reverse direction
            index.put(directedKey(seg.getToNodeCode(), seg.getFromNodeCode()), seg);
        }
        return index;
    }

    /**
     * 没有路段可参照时的回退速度：**13.19 km/h 是实测值**，不是拍的数。
     *
     * <p>现役 124 条边（全部有速度限值，只有 10/15/20 三档，分布 16/79/29）按节点坐标算出长度后
     * 取**长度加权调和平均** = {@code Σlen / Σ(len/v) = 13.19}；而"按条数取算术平均"是 15.76，
     * 原代码写死的 15 也差不多。差在哪：慢边（10 km/h）虽然条数少但每条更长，算术平均把它们稀释掉了，
     * 于是 ETA 系统性偏快约 18%。本方法因此改成按长度加权（见下），回退值也改成同一个口径。
     */
    private static final double FALLBACK_SPEED_KMH = 13.19D;

    private long estimateTravelSeconds(List<ParkGeoTransformService.GeoPoint> polyline,
                                        List<String> nodePath,
                                        Map<String, RoadSegmentEntity> segmentIndex,
                                        Long parkId) {
        if (polyline.size() < 2) {
            return 0L;
        }
        double totalMeters = 0;
        for (int i = 1; i < polyline.size(); i++) {
            totalMeters += GeoPolygonUtils.haversineMeters(polyline.get(i - 1), polyline.get(i));
        }
        if (totalMeters <= 0D) {
            return 0L;
        }
        double fallbackMetersPerSec = FALLBACK_SPEED_KMH * 1000.0 / 3600.0;

        // 逐段按自己的限速走：时间 = Σ(段长 / 段限速)。段长来自两端节点经纬度（路段表没有长度列，
        // polyline_geojson 在现役 seed 里 124 条全为 NULL，见 §1.5 那条未闭合项）。
        double measuredMeters = 0D;
        double measuredSeconds = 0D;
        if (parkId != null && nodePath != null && nodePath.size() >= 2) {
            Map<String, ParkGeoTransformService.GeoPoint> nodeById = loadNodeCoordinates(parkId, nodePath);
            for (int i = 1; i < nodePath.size(); i++) {
                RoadSegmentEntity seg = segmentIndex.get(directedKey(nodePath.get(i - 1), nodePath.get(i)));
                if (seg == null || seg.getSpeedLimitKmh() == null || seg.getSpeedLimitKmh() <= 0) {
                    continue;
                }
                ParkGeoTransformService.GeoPoint from = nodeById.get(nodePath.get(i - 1));
                ParkGeoTransformService.GeoPoint to = nodeById.get(nodePath.get(i));
                if (from == null || to == null) {
                    continue;
                }
                double len = GeoPolygonUtils.haversineMeters(from, to);
                if (len <= 0D) {
                    continue;
                }
                measuredMeters += len;
                measuredSeconds += len / (seg.getSpeedLimitKmh() * 1000.0 / 3600.0);
            }
        }
        // 路线里没有被路网覆盖的部分（起终点接入段、外部 polyline）按回退速度补，
        // 而不是让一段路凭空消失 —— 之前就是这种"整条路按一个常数"的错法。
        double remainder = Math.max(0D, totalMeters - measuredMeters);
        return (long) Math.ceil(measuredSeconds + remainder / fallbackMetersPerSec);
    }

    private Map<String, ParkGeoTransformService.GeoPoint> loadNodeCoordinates(Long parkId, List<String> nodePath) {
        List<RoadNodeEntity> nodes = roadNodeMapper.selectList(new QueryWrapper<RoadNodeEntity>()
                .eq("park_id", parkId)
                .in("node_code", nodePath)
                .eq("deleted", 0));
        Map<String, ParkGeoTransformService.GeoPoint> byCode = new HashMap<>();
        for (RoadNodeEntity node : nodes) {
            if (node.getNodeCode() == null || node.getCoordLng() == null || node.getCoordLat() == null) {
                continue;
            }
            byCode.put(node.getNodeCode(),
                    new ParkGeoTransformService.GeoPoint(node.getCoordLng(), node.getCoordLat()));
        }
        return byCode;
    }

    private List<String> collectRiskPoints(Long parkId, List<String> nodePath) {
        if (nodePath == null || nodePath.isEmpty() || parkId == null) {
            return List.of();
        }
        // park_id 与 deleted 必须罩住两个分支：写成 .eq(park).in(from).or().in(to).eq(deleted)
        // 会被 SQL 优先级切成 (park AND from) OR (to AND deleted)，第二支没有园区约束 —— 会捞到别的园区。
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                .eq("park_id", parkId)
                .eq("deleted", 0)
                .and(w -> w.in("from_node_code", nodePath).or().in("to_node_code", nodePath)));
        List<String> risks = new ArrayList<>();
        for (RoadSegmentEntity seg : segments) {
            String accessState = seg.getAccessState();
            boolean isRisk = (accessState != null && !accessState.isBlank()
                    && !"DRIVABLE".equalsIgnoreCase(accessState))
                    || (seg.getGateCode() != null && !seg.getGateCode().isBlank());
            if (isRisk) {
                String label = seg.getFromNodeCode() + "->" + seg.getToNodeCode()
                        + (seg.getGateCode() != null ? " (gate=" + seg.getGateCode() + ")" : "")
                        + (accessState != null ? " [" + accessState + "]" : "");
                if (!risks.contains(label)) {
                    risks.add(label);
                }
            }
        }
        return risks;
    }

    private static String directedKey(String from, String to) {
        return from + ">" + to;
    }

    /** Helper: get the dominant road width for a node path (for collision check envelope). */
    public BigDecimal minRoadWidthMeters(Long parkId, List<String> nodePath) {
        if (nodePath == null || nodePath.isEmpty() || parkId == null) {
            return null;
        }
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                .eq("park_id", parkId)
                .eq("deleted", 0));
        BigDecimal min = null;
        for (RoadSegmentEntity seg : segments) {
            if (seg.getWidthMeters() == null) continue;
            boolean inPath = false;
            for (int i = 1; i < nodePath.size(); i++) {
                if (directedKey(nodePath.get(i - 1), nodePath.get(i)).equals(directedKey(seg.getFromNodeCode(), seg.getToNodeCode()))
                        || directedKey(nodePath.get(i - 1), nodePath.get(i)).equals(directedKey(seg.getToNodeCode(), seg.getFromNodeCode()))) {
                    inPath = true;
                    break;
                }
            }
            if (inPath && (min == null || seg.getWidthMeters().compareTo(min) < 0)) {
                min = seg.getWidthMeters();
            }
        }
        return min;
    }
}
