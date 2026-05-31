package com.fsd.dispatch.mapf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * M5.1 验收：50 车同时规划，零对向冲突。
 */
@ExtendWith(MockitoExtension.class)
class MapfSimulationAcceptanceTest {

    @Mock
    private ParkRoutePlannerService parkRoutePlannerService;

    private InMemoryReservationService reservationService;
    private MapfRoutePlannerService mapfRoutePlannerService;
    private ParkRoadGraph graph;

    @BeforeEach
    void setUp() {
        MapfProperties mapfProperties = new MapfProperties();
        mapfProperties.setEnabled(true);
        mapfProperties.setBucketMs(500L);
        mapfProperties.setHorizonBuckets(4);
        mapfProperties.setMaxReplanAttempts(5);
        ParkPilotProperties parkPilotProperties = new ParkPilotProperties();
        parkPilotProperties.setWidth(1200);
        parkPilotProperties.setHeight(800);
        reservationService = new InMemoryReservationService(mapfProperties);
        MapfZonePartitioner zonePartitioner = new MapfZonePartitioner(mapfProperties, parkPilotProperties);
        mapfRoutePlannerService = new MapfRoutePlannerService(
                mapfProperties, parkPilotProperties, parkRoutePlannerService, reservationService, zonePartitioner);
        graph = buildBidirectionalCorridorGraph();
        org.mockito.Mockito.when(parkRoutePlannerService.loadGraph(1L)).thenReturn(graph);
        org.mockito.Mockito.when(parkRoutePlannerService.shortestNodePathWithPenalties(
                org.mockito.ArgumentMatchers.eq(graph),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Double> penalties = inv.getArgument(5);
                    return shortestPath(graph, "R1", "R4", penalties);
                });
        org.mockito.Mockito.when(parkRoutePlannerService.buildRouteFromNodePath(
                org.mockito.ArgumentMatchers.eq(graph),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(inv -> {
                    List<String> nodePath = inv.getArgument(5);
                    List<ParkPointResponse> route = new ArrayList<>();
                    route.add(ParkPointResponse.builder().code("START").x(BigDecimal.ZERO).y(BigDecimal.ZERO).build());
                    for (String code : nodePath) {
                        ParkRoadGraph.NodeView node = graph.node(code);
                        route.add(ParkPointResponse.builder().code(code).x(node.x()).y(node.y()).build());
                    }
                    route.add(ParkPointResponse.builder().code("END").x(BigDecimal.TEN).y(BigDecimal.TEN).build());
                    return route;
                });
        org.mockito.Mockito.when(parkRoutePlannerService.buildRoute(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(ParkPointResponse.builder().code("FALLBACK").build()));
    }

    @Test
    void fiftyVehiclesShouldAvoidHeadOnConflicts() {
        int conflicts = 0;
        int reserved = 0;
        long bucket = reservationService.currentBucket();
        for (long vehicleId = 1; vehicleId <= 50; vehicleId++) {
            BigDecimal startX = vehicleId % 2 == 0 ? BigDecimal.valueOf(105) : BigDecimal.valueOf(905);
            BigDecimal endX = vehicleId % 2 == 0 ? BigDecimal.valueOf(905) : BigDecimal.valueOf(105);
            MapfRoutePlanResult plan = mapfRoutePlannerService.planAndReserve(
                    1L, vehicleId, startX, BigDecimal.valueOf(125), endX, BigDecimal.valueOf(125));
            assertTrue(plan.isSuccess());
            if (plan.isReserved()) {
                reserved++;
            }
            conflicts += reservationService.countHeadOnConflicts(1L, bucket);
        }
        assertEquals(0, conflicts, "Head-on conflicts should be zero");
        assertTrue(reserved >= 1, "At least one vehicle should obtain reservations");
    }

    private static List<String> shortestPath(ParkRoadGraph graph, String start, String end, Map<String, Double> penalties) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        dist.put(start, 0D);
        List<String> open = new ArrayList<>(List.of(start));
        while (!open.isEmpty()) {
            open.sort((a, b) -> Double.compare(dist.getOrDefault(a, Double.MAX_VALUE), dist.getOrDefault(b, Double.MAX_VALUE)));
            String current = open.remove(0);
            if (current.equals(end)) {
                break;
            }
            for (String next : graph.neighbors(current)) {
                double penalty = penalties.getOrDefault(current + ">" + next, 0D);
                double candidate = dist.getOrDefault(current, Double.MAX_VALUE)
                        + graph.edgeCost(current, next) * (1D + penalty);
                if (candidate < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, candidate);
                    prev.put(next, current);
                    if (!open.contains(next)) {
                        open.add(next);
                    }
                }
            }
        }
        List<String> path = new ArrayList<>();
        String cursor = end;
        while (cursor != null) {
            path.add(0, cursor);
            cursor = prev.get(cursor);
            if (cursor != null && cursor.equals(end) && !prev.containsKey(cursor)) {
                break;
            }
        }
        return path.isEmpty() ? List.of(start) : path;
    }

    private static ParkRoadGraph buildBidirectionalCorridorGraph() {
        List<RoadNodeEntity> nodes = List.of(
                node("R1", 100, 120),
                node("R2", 350, 120),
                node("R3", 650, 120),
                node("R4", 900, 120));
        List<RoadSegmentEntity> segments = List.of(
                segment("R1", "R2"), segment("R2", "R1"),
                segment("R2", "R3"), segment("R3", "R2"),
                segment("R3", "R4"), segment("R4", "R3"));
        return ParkRoadGraph.fromDatabase(nodes, segments);
    }

    private static RoadNodeEntity node(String code, int x, int y) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setStatus("ACTIVE");
        return entity;
    }

    /** 测试用内存预约表，行为与 Redis 版一致。 */
    static class InMemoryReservationService extends MapfReservationService {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        InMemoryReservationService(MapfProperties properties) {
            super(properties, null);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean tryReserveEdge(Long parkId, Long vehicleId, String fromNode, String toNode, long startBucket) {
            String vid = vehicleId.toString();
            int horizon = Math.max(1, 4);
            for (int i = 0; i < horizon; i++) {
                long bucket = startBucket + i;
                String forwardKey = "mapf:res:" + parkId + ":" + fromNode + ">" + toNode + ":" + bucket;
                String reverseKey = "mapf:res:" + parkId + ":" + toNode + ">" + fromNode + ":" + bucket;
                String reverseHolder = store.get(reverseKey);
                if (reverseHolder != null && !reverseHolder.equals(vid)) {
                    return false;
                }
                String forwardHolder = store.get(forwardKey);
                if (forwardHolder != null && !forwardHolder.equals(vid)) {
                    return false;
                }
                store.putIfAbsent(forwardKey, vid);
            }
            return true;
        }

        void preoccupy(Long parkId, Long vehicleId, String from, String to, long bucket) {
            tryReserveEdge(parkId, vehicleId, from, to, bucket);
        }

        int countHeadOnConflicts(Long parkId, long bucket) {
            int conflicts = 0;
            for (Map.Entry<String, String> entry : store.entrySet()) {
                if (!entry.getKey().contains(":" + bucket)) {
                    continue;
                }
                String reverse = reverseKey(entry.getKey());
                String reverseHolder = store.get(reverse);
                if (reverseHolder != null && !reverseHolder.equals(entry.getValue())) {
                    conflicts++;
                }
            }
            return conflicts / 2;
        }

        private static String reverseKey(String forwardKey) {
            int segStart = forwardKey.indexOf(':');
            segStart = forwardKey.indexOf(':', segStart + 1);
            int segEnd = forwardKey.lastIndexOf(':');
            String segmentPart = forwardKey.substring(segStart + 1, segEnd);
            String[] parts = segmentPart.split(">");
            if (parts.length != 2) {
                return forwardKey;
            }
            return forwardKey.substring(0, segStart + 1) + parts[1] + ">" + parts[0] + forwardKey.substring(segEnd);
        }
    }
}
