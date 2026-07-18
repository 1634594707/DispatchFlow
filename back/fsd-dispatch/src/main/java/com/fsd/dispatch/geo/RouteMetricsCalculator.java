package com.fsd.dispatch.geo;

import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.entity.StationEntity;
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

    public RouteMetricsCalculator(RoadSegmentMapper roadSegmentMapper) {
        this.roadSegmentMapper = roadSegmentMapper;
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

        // Travel time: based on per-segment speed limits (fallback 15 km/h)
        Map<String, RoadSegmentEntity> segmentByNodePair = loadSegmentIndex(parkId);
        long travelSeconds = estimateTravelSeconds(polyline, nodePath, segmentByNodePair);

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

    private long estimateTravelSeconds(List<ParkGeoTransformService.GeoPoint> polyline,
                                        List<String> nodePath,
                                        Map<String, RoadSegmentEntity> segmentIndex) {
        if (polyline.size() < 2) {
            return 0L;
        }
        // Average speed limit fallback (15 km/h) — typical for park internal roads
        int defaultSpeedKmh = 15;
        double totalMeters = 0;
        for (int i = 1; i < polyline.size(); i++) {
            totalMeters += GeoPolygonUtils.haversineMeters(polyline.get(i - 1), polyline.get(i));
        }
        // Try to find the dominant speed limit from the node path
        int speedKmh = defaultSpeedKmh;
        if (nodePath != null && nodePath.size() >= 2) {
            int sum = 0;
            int count = 0;
            for (int i = 1; i < nodePath.size(); i++) {
                RoadSegmentEntity seg = segmentIndex.get(directedKey(nodePath.get(i - 1), nodePath.get(i)));
                if (seg != null && seg.getSpeedLimitKmh() != null) {
                    sum += seg.getSpeedLimitKmh();
                    count++;
                }
            }
            if (count > 0) {
                speedKmh = Math.max(5, sum / count);
            }
        }
        // time = distance / speed
        double speedMetersPerSec = speedKmh * 1000.0 / 3600.0;
        if (speedMetersPerSec <= 0) {
            return 0L;
        }
        return (long) (totalMeters / speedMetersPerSec);
    }

    private List<String> collectRiskPoints(Long parkId, List<String> nodePath) {
        if (nodePath == null || nodePath.isEmpty() || parkId == null) {
            return List.of();
        }
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                .eq("park_id", parkId)
                .in("from_node_code", nodePath)
                .or()
                .in("to_node_code", nodePath)
                .eq("deleted", 0));
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
