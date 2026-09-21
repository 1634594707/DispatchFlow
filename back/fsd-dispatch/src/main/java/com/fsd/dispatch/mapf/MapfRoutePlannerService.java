package com.fsd.dispatch.mapf;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * MAPF 冲突感知路径规划：A* 最短路 + Redis 时空预约 + 冲突边惩罚迭代重规划（M5.1–M5.2）。
 *
 * <p>寻路委托 {@link ParkRoutePlannerService}：图内节点度量一致时走 A*（h = 到终点直线距离），
 * 否则回退 Dijkstra；冲突惩罚只增大边权，不破坏 A* 的可采纳性。
 */
@Service
public class MapfRoutePlannerService {

    private final MapfProperties mapfProperties;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final MapfReservationService reservationService;
    private final MapfReservationMetrics metrics;

    public MapfRoutePlannerService(MapfProperties mapfProperties,
                                   ParkRoutePlannerService parkRoutePlannerService,
                                   MapfReservationService reservationService,
                                   MapfReservationMetrics metrics) {
        this.mapfProperties = mapfProperties;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.reservationService = reservationService;
        this.metrics = metrics;
    }

    public boolean isEnabled() {
        return mapfProperties.isEnabled() && reservationService.isEnabled();
    }

    public MapfRoutePlanResult planAndReserve(Long parkId, Long vehicleId,
                                              BigDecimal startX, BigDecimal startY,
                                              BigDecimal endX, BigDecimal endY) {
        long started = System.nanoTime();
        if (!isEnabled() || vehicleId == null) {
            metrics.disabled();
            List<ParkPointResponse> route = parkRoutePlannerService.buildRoute(parkId, startX, startY, endX, endY);
            return MapfRoutePlanResult.builder()
                    .route(route)
                    .reserved(false)
                    .replanAttempts(0)
                    .planningTimeMs(elapsedMs(started))
                    .build();
        }
        ParkRoadGraph graph = parkRoutePlannerService.loadGraph(parkId);
        Map<String, Double> penalties = new HashMap<>();
        int attempts = 0;
        int maxAttempts = Math.max(1, mapfProperties.getMaxReplanAttempts());
        while (attempts < maxAttempts) {
            attempts++;
            List<String> nodePath = parkRoutePlannerService.shortestNodePathWithPenalties(graph, startX, startY, endX, endY, penalties);
            if (nodePath.isEmpty()) {
                break;
            }
            if (tryReserveNodePath(parkId, vehicleId, graph, nodePath)) {
                metrics.reserved();
                List<ParkPointResponse> route = parkRoutePlannerService.buildRouteFromNodePath(
                        graph, startX, startY, endX, endY, nodePath);
                return MapfRoutePlanResult.builder()
                        .route(route)
                        .reserved(true)
                        .replanAttempts(attempts)
                        .planningTimeMs(elapsedMs(started))
                        .build();
            }
            penalizePath(penalties, nodePath);
        }
        // 重规划用尽：仍然返回一条**没有预约**的路线，派单照走 —— MAPF 从不拦单。
        // 该不该拦是业务口径（§7.2，待本人定），但这一支必须先能被看见，所以下面这一行不是装饰。
        metrics.conflict();
        List<ParkPointResponse> fallback = parkRoutePlannerService.buildRoute(parkId, startX, startY, endX, endY);
        return MapfRoutePlanResult.builder()
                .route(fallback)
                .reserved(false)
                .replanAttempts(attempts)
                .planningTimeMs(elapsedMs(started))
                .build();
    }

    private boolean tryReserveNodePath(Long parkId, Long vehicleId, ParkRoadGraph graph,
                                       List<String> nodePath) {
        if (nodePath.size() < 2) {
            return true;
        }
        long bucket = reservationService.currentBucket();
        // 单位统一（§7.2 那条 MAPF 项）：分子是米（distanceMetersTo 恒为米），分母就必须是米/秒。
        // 以前这里除的是 fsd.park.vehicle-speed-px-per-second —— 那是示意坐标上的动画/仿真速度，
        // 与真实路网速度不是一回事，8 px/s 相对实测 13.19 km/h 快了 2.19 倍，
        // 于是每条边的预约窗口只覆盖真实占位时间的 37%–62%（随航向变），冲突几乎挡不住车。
        double speedMetersPerSecond = Math.max(0.1D, mapfProperties.getVehicleSpeedMetersPerSecond());
        double bucketSeconds = Math.max(0.001D, mapfProperties.getBucketMs() / 1000.0);
        long cursor = bucket;
        for (int i = 0; i < nodePath.size() - 1; i++) {
            String from = nodePath.get(i);
            String to = nodePath.get(i + 1);
            if (!reservationService.tryReserveEdge(parkId, vehicleId, from, to, cursor)) {
                return false;
            }
            ParkRoadGraph.NodeView fromNode = graph.node(from);
            ParkRoadGraph.NodeView toNode = graph.node(to);
            if (fromNode != null && toNode != null) {
                double edgeMeters = fromNode.distanceMetersTo(toNode);
                long buckets = Math.max(1L, Math.round(edgeMeters / speedMetersPerSecond / bucketSeconds));
                cursor += buckets;
            } else {
                cursor += 1;
            }
        }
        return true;
    }

    private void penalizePath(Map<String, Double> penalties, List<String> nodePath) {
        double multiplier = mapfProperties.getConflictPenaltyMultiplier();
        for (int i = 0; i < nodePath.size() - 1; i++) {
            String key = directedKey(nodePath.get(i), nodePath.get(i + 1));
            penalties.merge(key, multiplier, Double::sum);
        }
    }

    private static String directedKey(String from, String to) {
        return from + ">" + to;
    }

    private static long elapsedMs(long startedNano) {
        return (System.nanoTime() - startedNano) / 1_000_000L;
    }
}
