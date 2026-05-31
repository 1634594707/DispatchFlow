package com.fsd.dispatch.mapf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapfRoutePlannerServiceTest {

    @Mock
    private ParkRoutePlannerService parkRoutePlannerService;

    private MapfRoutePlannerService mapfRoutePlannerService;
    private MapfSimulationAcceptanceTest.InMemoryReservationService reservationService;

    @BeforeEach
    void setUp() {
        MapfProperties mapfProperties = new MapfProperties();
        mapfProperties.setEnabled(true);
        mapfProperties.setMaxReplanAttempts(3);
        ParkPilotProperties parkPilotProperties = new ParkPilotProperties();
        parkPilotProperties.setWidth(1200);
        parkPilotProperties.setHeight(800);
        reservationService = new MapfSimulationAcceptanceTest.InMemoryReservationService(mapfProperties);
        MapfZonePartitioner zonePartitioner = new MapfZonePartitioner(mapfProperties, parkPilotProperties);
        mapfRoutePlannerService = new MapfRoutePlannerService(
                mapfProperties, parkPilotProperties, parkRoutePlannerService, reservationService, zonePartitioner);
    }

    @Test
    void shouldReserveConflictFreeRoute() {
        ParkRoadGraph graph = buildLineGraph();
        when(parkRoutePlannerService.loadGraph(1L)).thenReturn(graph);
        when(parkRoutePlannerService.shortestNodePathWithPenalties(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("R1", "R2", "R3"));
        when(parkRoutePlannerService.buildRouteFromNodePath(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        ParkPointResponse.builder().code("START").x(BigDecimal.ONE).y(BigDecimal.ONE).build(),
                        ParkPointResponse.builder().code("R3").x(BigDecimal.TEN).y(BigDecimal.TEN).build()));

        MapfRoutePlanResult result = mapfRoutePlannerService.planAndReserve(
                1L, 10L,
                BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125));

        assertTrue(result.isSuccess());
        assertTrue(result.isReserved());
    }

    @Test
    void shouldReplanWhenHeadOnConflictDetected() {
        ParkRoadGraph graph = buildLineGraph();
        when(parkRoutePlannerService.loadGraph(1L)).thenReturn(graph);
        when(parkRoutePlannerService.shortestNodePathWithPenalties(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("R1", "R2"))
                .thenReturn(List.of("R1", "R2", "R3"));
        when(parkRoutePlannerService.buildRoute(any(), any(), any(), any(), any()))
                .thenReturn(List.of(ParkPointResponse.builder().code("FALLBACK").build()));
        reservationService.preoccupy(1L, 99L, "R1", "R2", reservationService.currentBucket());

        MapfRoutePlanResult result = mapfRoutePlannerService.planAndReserve(
                1L, 10L,
                BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125));

        assertTrue(result.isSuccess());
        assertTrue(result.getReplanAttempts() >= 2);
    }

    @Test
    void zonePartitionerShouldAssignGridZones() {
        MapfProperties mapfProperties = new MapfProperties();
        mapfProperties.setZoneGridSize(4);
        ParkPilotProperties parkPilotProperties = new ParkPilotProperties();
        parkPilotProperties.setWidth(1200);
        parkPilotProperties.setHeight(800);
        MapfZonePartitioner partitioner = new MapfZonePartitioner(mapfProperties, parkPilotProperties);
        Map<String, String> zones = partitioner.partition(buildLineGraph());
        assertFalse(zones.isEmpty());
        assertEquals("Z0_0", zones.get("R1"));
        assertEquals("Z0_1", zones.get("R3"));
    }

    private static ParkRoadGraph buildLineGraph() {
        List<RoadNodeEntity> nodes = List.of(
                node("R1", 100, 120),
                node("R2", 220, 120),
                node("R3", 420, 120));
        List<RoadSegmentEntity> segments = List.of(
                segment("R1", "R2"),
                segment("R2", "R3"));
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
