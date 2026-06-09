package com.fsd.dispatch.mapf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * M5.2 压测：200 车规划 P95 &lt; 500ms（纯内存图 + 内存预约表，无 Redis I/O）。
 */
@ExtendWith(MockitoExtension.class)
class MapfLoadTest {

    @Mock
    private ParkRoutePlannerService parkRoutePlannerService;

    private MapfRoutePlannerService mapfRoutePlannerService;
    private ParkRoadGraph graph;

    @BeforeEach
    void setUp() {
        MapfProperties mapfProperties = new MapfProperties();
        mapfProperties.setEnabled(true);
        mapfProperties.setMaxReplanAttempts(3);
        ParkPilotProperties parkPilotProperties = new ParkPilotProperties();
        parkPilotProperties.setWidth(1200);
        parkPilotProperties.setHeight(800);
        MapfSimulationAcceptanceTest.InMemoryReservationService reservationService =
                new MapfSimulationAcceptanceTest.InMemoryReservationService(mapfProperties);
        mapfRoutePlannerService = new MapfRoutePlannerService(
                mapfProperties, parkPilotProperties, parkRoutePlannerService, reservationService);
        graph = buildGridGraph();
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
                    BigDecimal endX = inv.getArgument(3);
                    String endNode = endX.doubleValue() > 600 ? "R6" : "R2";
                    return shortestPath(graph, "R1", endNode, penalties);
                });
        org.mockito.Mockito.when(parkRoutePlannerService.buildRouteFromNodePath(
                org.mockito.ArgumentMatchers.eq(graph),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(ParkPointResponse.builder().code("START").build()));
        org.mockito.Mockito.when(parkRoutePlannerService.buildRoute(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(ParkPointResponse.builder().code("FALLBACK").build()));
    }

    @Test
    void twoHundredVehiclePlanningP95ShouldBeUnder500Ms() {
        List<Long> durations = new ArrayList<>();
        for (long vehicleId = 1; vehicleId <= 200; vehicleId++) {
            long start = System.nanoTime();
            MapfRoutePlanResult plan = mapfRoutePlannerService.planAndReserve(
                    1L, vehicleId,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(120),
                    vehicleId % 2 == 0 ? BigDecimal.valueOf(900) : BigDecimal.valueOf(300),
                    BigDecimal.valueOf(120));
            durations.add(plan.getPlanningTimeMs() > 0
                    ? plan.getPlanningTimeMs()
                    : (System.nanoTime() - start) / 1_000_000L);
            assertTrue(plan.isSuccess());
        }
        Collections.sort(durations);
        long p95 = durations.get((int) Math.floor(durations.size() * 0.95) - 1);
        assertTrue(p95 < 500, "P95 planning time was " + p95 + "ms");
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
        for (String cursor = end; cursor != null; cursor = prev.get(cursor)) {
            path.add(0, cursor);
        }
        return path.isEmpty() ? List.of(start) : path;
    }

    private static ParkRoadGraph buildGridGraph() {
        List<RoadNodeEntity> nodes = List.of(
                node("R1", 100, 120), node("R2", 350, 120), node("R3", 100, 320),
                node("R4", 350, 320), node("R5", 650, 120), node("R6", 900, 120));
        List<RoadSegmentEntity> segments = List.of(
                segment("R1", "R2"), segment("R2", "R1"),
                segment("R1", "R3"), segment("R3", "R1"),
                segment("R2", "R4"), segment("R4", "R2"),
                segment("R3", "R4"), segment("R4", "R3"),
                segment("R2", "R5"), segment("R5", "R2"),
                segment("R5", "R6"), segment("R6", "R5"));
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
}
