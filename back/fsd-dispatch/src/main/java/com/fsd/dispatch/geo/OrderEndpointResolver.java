package com.fsd.dispatch.geo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.StationType;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkGeofenceResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.dto.OrderEndpointResolution;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任意点下单的受理判据：把用户给的 GCJ-02 坐标变成一个**跑得通**的可派单位置。
 *
 * <p>三条判据，全部在受理时判完（不留到派单才发现接了张死单）：
 * <ol>
 *   <li><b>在范围内</b>：坐标要落在某片 ACTIVE 且可派单的围栏里；</li>
 *   <li><b>吸附得到</b>：附近有落在 {@code fsd.park.geo.order-snap-meters} 半径内的图节点
 *       （半径的来历见 {@link ParkPilotProperties.GeoConfig#getOrderSnapMeters()}）；</li>
 *   <li><b>两端连得通</b>：取货端与送货端之间 {@link ParkRoutePlannerService#isReachable} 为真 ——
 *       刻意复用派单侧同一个判据，避免"受理放行、派单拒收"这种两套尺子对不上的错。</li>
 * </ol>
 *
 * <p><b>为什么吸附结果要落成一条 t_station 记录</b>：派单、仿真车队、VDA5050 指令、工单详情
 * 四条链路全都从 {@code order.pickupPointId -> t_station} 取位置。给它们各加一套"端点"分支，
 * 等于把同一个判定抄四遍 —— 而抄四遍的东西一定会在第五处不一致。落成一个点之后，
 * 站点表就从"派单前提"退化成"已知位置的登记表"，这正是任意点下单要的形态，
 * 顺带让地图上第一次能看到"订单来自哪里"而不是一坨预登记点。
 *
 * <p><b>为什么按节点去重而不是按坐标</b>：一个节点一条记录 ⇒ 表的上限就是图节点数（现役 446），
 * 不会随订单数无界增长；用户点的精确位置仍然逐单存在 {@code t_order.pickup_lng/lat} 上，
 * 展示与事后校准都不丢信息。
 */
@Service
public class OrderEndpointResolver {

    /** 每端最多试几个候选节点。超出即认为吸附不到，宁可拒单也不猜。 */
    private static final int CANDIDATE_LIMIT = 6;

    /** 自动落点的编码前缀：与人工登记的 ZJF-* 站点一眼可分。 */
    private static final String GEO_CODE_PREFIX = "GEO-";

    private final ParkRoutePlannerService routePlannerService;
    private final ParkGeofenceService geofenceService;
    private final ParkStationService parkStationService;
    private final StationMapper stationMapper;
    private final ParkPilotProperties properties;

    public OrderEndpointResolver(ParkRoutePlannerService routePlannerService,
                                 ParkGeofenceService geofenceService,
                                 ParkStationService parkStationService,
                                 StationMapper stationMapper,
                                 ParkPilotProperties properties) {
        this.routePlannerService = routePlannerService;
        this.geofenceService = geofenceService;
        this.parkStationService = parkStationService;
        this.stationMapper = stationMapper;
        this.properties = properties;
    }

    /**
     * @return null 表示两端都是登记站点，走今天的老路径；否则给出解析后的两个站点 ID 与吸附审计值
     * @throws BusinessException ORDER_ENDPOINT_OUT_OF_SERVICE_AREA / _SNAP_FAILED / _UNREACHABLE /
     *                           ORDER_ROAD_GRAPH_EMPTY
     */
    @Transactional
    public Accepted resolveForAcceptance(Long parkId,
                                         Long pickupStationId, BigDecimal pickupLng, BigDecimal pickupLat,
                                         Long dropoffStationId, BigDecimal dropoffLng, BigDecimal dropoffLat) {
        boolean pickupByCoord = pickupStationId == null && pickupLng != null && pickupLat != null;
        boolean dropoffByCoord = dropoffStationId == null && dropoffLng != null && dropoffLat != null;
        if (!pickupByCoord && !dropoffByCoord) {
            return null;
        }
        double threshold = properties.getGeo().getOrderSnapMeters();
        ParkRoadGraph graph = routePlannerService.loadGraph(parkId);
        if (graph == null || graph.isEmpty()) {
            throw new BusinessException("ORDER_ROAD_GRAPH_EMPTY", "园区路网图为空，任意点下单暂时无法受理");
        }
        if (pickupByCoord) {
            assertInsideServiceArea(parkId, pickupLng, pickupLat, "取货");
        }
        if (dropoffByCoord) {
            assertInsideServiceArea(parkId, dropoffLng, dropoffLat, "送货");
        }

        List<Anchor> pickupAnchors = anchors(graph, parkId, pickupStationId, pickupLng, pickupLat, pickupByCoord, threshold, "取货");
        List<Anchor> dropoffAnchors = anchors(graph, parkId, dropoffStationId, dropoffLng, dropoffLat, dropoffByCoord, threshold, "送货");

        // 两端候选都已按吸附距离升序，所以第一对连得通的就是总偏差最小的解。
        for (Anchor pickup : pickupAnchors) {
            for (Anchor dropoff : dropoffAnchors) {
                if (pickup.nodeCode != null && pickup.nodeCode.equals(dropoff.nodeCode)) {
                    continue;
                }
                if (!routePlannerService.isReachable(parkId, pickup.x, pickup.y, dropoff.x, dropoff.y)) {
                    continue;
                }
                return new Accepted(
                        materialize(parkId, pickup, pickupByCoord ? pickupLng : null, pickupByCoord ? pickupLat : null, "取货"),
                        materialize(parkId, dropoff, dropoffByCoord ? dropoffLng : null, dropoffByCoord ? dropoffLat : null, "送货"),
                        pickup, dropoff);
            }
        }
        throw new BusinessException("ORDER_ENDPOINT_UNREACHABLE",
                String.format("取货点与送货点在自建路网上连不通（最近候选吸附 %.0f m / %.0f m，半径 %.0f m）",
                        best(pickupAnchors), best(dropoffAnchors), threshold));
    }

    /** 受理结果：两个可直接派单的站点 ID，外加要落到 t_order 上的吸附审计字段。 */
    public record Accepted(Long pickupStationId, Long dropoffStationId,
                           Anchor pickup, Anchor dropoff) {

        public OrderEndpointResolution auditResolution() {
            return new OrderEndpointResolution(pickup.nodeCode, dropoff.nodeCode,
                    pickup.snapMeters(), dropoff.snapMeters());
        }
    }

    /** 一个候选端点。{@code stationId} 非空表示它本来就是登记站点，不需要落新点。 */
    public record Anchor(Long stationId, String nodeCode, BigDecimal x, BigDecimal y, BigDecimal snapMeters) {
    }

    /** 站点端只有一个候选（站点自身的示意坐标），今天的判定路径因此逐字不变。 */
    private List<Anchor> anchors(ParkRoadGraph graph, Long parkId, Long stationId, BigDecimal lng, BigDecimal lat,
                                 boolean byCoord, double threshold, String label) {
        if (!byCoord) {
            ParkStationResponse station = parkStationService.requireStation(stationId);
            return List.of(new Anchor(stationId, null, station.getX(), station.getY(), null));
        }
        GeoPoint point = new GeoPoint(lng, lat);
        List<Anchor> out = new ArrayList<>();
        for (ParkRoadGraph.NodeView node : graph.nodes().values()) {
            if (node.coordLng() == null || node.coordLat() == null || node.x() == null || node.y() == null) {
                continue;
            }
            double d = GeoPolygonUtils.haversineMeters(point, new GeoPoint(node.coordLng(), node.coordLat()));
            if (d <= threshold) {
                out.add(new Anchor(null, node.code(), node.x(), node.y(),
                        BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        if (out.isEmpty()) {
            throw new BusinessException("ORDER_ENDPOINT_SNAP_FAILED",
                    String.format("%s点 %.6f,%.6f 半径 %.0f m 内没有可派单路网节点，无法派车", label,
                            lng.doubleValue(), lat.doubleValue(), threshold));
        }
        out.sort(Comparator.comparingDouble(a -> a.snapMeters().doubleValue()));
        return List.copyOf(out).subList(0, Math.min(CANDIDATE_LIMIT, out.size()));
    }

    /** 吸附成功 <=> 该节点已登记成一个可派单点；已存在就复用，不重复插。 */
    private Long materialize(Long parkId, Anchor anchor, BigDecimal rawLng, BigDecimal rawLat, String label) {
        if (anchor.stationId() != null) {
            return anchor.stationId();
        }
        String code = GEO_CODE_PREFIX + anchor.nodeCode();
        // 不按 deleted 过滤：uk_park_station_code 是 (park_id, station_code)，漏掉一条软删行
        // 会让下面的 insert 直接撞唯一键。
        StationEntity existing = stationMapper.selectOne(new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getParkId, parkId)
                .eq(StationEntity::getStationCode, code)
                .last("LIMIT 1"));
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted()) || !"ACTIVE".equals(existing.getStatus())) {
                existing.setDeleted(0);
                existing.setStatus("ACTIVE");
                stationMapper.updateById(existing);
            }
            return existing.getId();
        }
        StationEntity station = new StationEntity();
        station.setParkId(parkId);
        station.setStationCode(code);
        station.setStationName(label + "·路网节点 " + anchor.nodeCode());
        // GEO_POINT 既不是 HUB/BUFFER/MOTHERSHIP，泊位容量那条判定会自然跳过它（HubCapacityServiceImpl）。
        station.setStationType(StationType.GEO_POINT.name());
        station.setCoordX(anchor.x());
        station.setCoordY(anchor.y());
        station.setCoordLng(rawLng);
        station.setCoordLat(rawLat);
        // area 必须是 ZJF：`isGeoDeliveryStation`（ParkStationServiceImpl:260 / PilotFleetSupport:31）
        // 用 `area=="ZJF" || code 以 ZJF- 开头` 判"这是地理派单点"，GEO-* 两个条件都不满足，
        // 不设 area 会让自动落点被当成非地理点，配送区判定整条跳过。
        station.setArea("ZJF");
        station.setAnchorNodeCode(anchor.nodeCode());
        station.setStatus("ACTIVE");
        // 自动落点没有人工目视过进出口，可信度上限就是 C（与 §13.31 那批新点同一口径）。
        station.setStationConfidence("C");
        station.setSortOrder(900);
        station.setRemark("任意点下单自动落点：吸附到 " + anchor.nodeCode()
                + (anchor.snapMeters() == null ? "" : "（吸附 " + anchor.snapMeters() + " m）"));
        station.setDeleted(0);
        stationMapper.insert(station);
        return station.getId();
    }

    /**
     * 范围判据只吃"ACTIVE 且可派单"的围栏并集，不吃 {@code DEFAULT-BOUNDARY} 那层展示包络 ——
     * 后者是给人看的框，实测连现役两片商圈都不完全包住（深国际整片在框南 2.77 km 外）。
     */
    private void assertInsideServiceArea(Long parkId, BigDecimal lng, BigDecimal lat, String label) {
        List<ParkGeofenceResponse> fences = geofenceService.listActiveByPark(parkId);
        boolean inside = fences.stream()
                .filter(fence -> Boolean.TRUE.equals(fence.getDispatchable()))
                .anyMatch(fence -> inside(fence, lng, lat));
        if (!inside) {
            throw new BusinessException("ORDER_ENDPOINT_OUT_OF_SERVICE_AREA",
                    String.format("%s点 %.6f,%.6f 不在任何可派单服务范围内", label,
                            lng.doubleValue(), lat.doubleValue()));
        }
    }

    private static boolean inside(ParkGeofenceResponse fence, BigDecimal lng, BigDecimal lat) {
        List<List<BigDecimal>> polygon = fence.getPolygon();
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        List<double[]> vertices = new ArrayList<>(polygon.size());
        for (List<BigDecimal> point : polygon) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                return false;
            }
            vertices.add(new double[]{point.get(0).doubleValue(), point.get(1).doubleValue()});
        }
        return GeoPolygonUtils.contains(vertices, lng, lat);
    }

    private static double best(List<Anchor> anchors) {
        return anchors.isEmpty() ? Double.NaN : anchors.get(0).snapMeters().doubleValue();
    }
}
