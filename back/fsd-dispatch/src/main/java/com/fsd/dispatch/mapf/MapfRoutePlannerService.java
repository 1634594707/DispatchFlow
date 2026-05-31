package com.fsd.dispatch.mapf;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * MAPF 冲突感知路径规划：A* + Redis 时空预约 + 冲突重规划（M5.1–M5.2）。
 */
@Service
public class MapfRoutePlannerService {

    private final MapfProperties mapfProperties;
    private final ParkPilotProperties parkPilotProperties;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final MapfReservationService reservationService;
    private final MapfZonePartitioner zonePartitioner;

    public MapfRoutePlannerService(MapfProperties mapfProperties,
                                   ParkPilotProperties parkPilotProperties,
                                   ParkRoutePlannerService parkRoutePlannerService,
                                   MapfReservationService reservationService,
                                   MapfZonePartitioner zonePartitioner) {
        this.mapfProperties = mapfProperties;
        this.parkPilotProperties = parkPilotProperties;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.reservationService = reservationService;
        this.zonePartitioner = zonePartitioner;
    }

    public boolean isEnabled() {
        return mapfProperties.isEnabled() && reservationService.isEnabled();
    }

    public MapfRoutePlanResult planAndReserve(Long parkId, Long vehicleId,
                                              BigDecimal startX, BigDecimal startY,
                                              BigDecimal endX, BigDecimal endY) {
        long started = System.nanoTime();
        if (!isEnabled() || vehicleId == null) {
            List<ParkPointResponse> route = parkRoutePlannerService.buildRoute(parkId, startX, startY, endX, endY);
            return MapfRoutePlanResult.builder()
                    .route(route)
                    .reserved(false)
                    .replanAttempts(0)
                    .planningTimeMs(elapsedMs(started))
                    .build();
        }
        ParkRoadGraph graph = parkRoutePlannerService.loadGraph(parkId);
        Map<String, String> nodeZones = zonePartitioner.partition(graph);
        Map<String, Double> penalties = new HashMap<>();
        int attempts = 0;
        int maxAttempts = Math.max(1, mapfProperties.getMaxReplanAttempts());
        List<String> nodePath = List.of();
        while (attempts < maxAttempts) {
            attempts++;
            nodePath = parkRoutePlannerService.shortestNodePathWithPenalties(graph, startX, startY, endX, endY, penalties);
            if (nodePath.isEmpty()) {
                break;
            }
            if (tryReserveNodePath(parkId, vehicleId, graph, nodePath, nodeZones)) {
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
        List<ParkPointResponse> fallback = parkRoutePlannerService.buildRoute(parkId, startX, startY, endX, endY);
        return MapfRoutePlanResult.builder()
                .route(fallback)
                .reserved(false)
                .replanAttempts(attempts)
                .planningTimeMs(elapsedMs(started))
                .build();
    }

    private boolean tryReserveNodePath(Long parkId, Long vehicleId, ParkRoadGraph graph,
                                       List<String> nodePath, Map<String, String> nodeZones) {
        if (nodePath.size() < 2) {
            return true;
        }
        long bucket = reservationService.currentBucket();
        double speed = Math.max(1D, mapfProperties.getVehicleSpeedPxPerSecond());
        if (parkPilotProperties.getVehicleSpeedPxPerSecond() != null) {
            speed = parkPilotProperties.getVehicleSpeedPxPerSecond().doubleValue();
        }
        long cursor = bucket;
        Set<String> visitedZones = new HashSet<>();
        for (int i = 0; i < nodePath.size() - 1; i++) {
            String from = nodePath.get(i);
            String to = nodePath.get(i + 1);
            visitedZones.add(zonePartitioner.zoneOfNode(nodeZones, from));
            if (!reservationService.tryReserveEdge(parkId, vehicleId, from, to, cursor)) {
                return false;
            }
            ParkRoadGraph.NodeView fromNode = graph.node(from);
            ParkRoadGraph.NodeView toNode = graph.node(to);
            if (fromNode != null && toNode != null) {
                double edgePx = fromNode.distanceTo(toNode);
                long buckets = Math.max(1L, Math.round(edgePx / speed / (mapfProperties.getBucketMs() / 1000.0)));
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
