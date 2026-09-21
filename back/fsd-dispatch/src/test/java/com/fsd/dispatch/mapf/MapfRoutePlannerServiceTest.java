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
import org.mockito.ArgumentCaptor;
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
        mapfRoutePlannerService = new MapfRoutePlannerService(
                mapfProperties, parkRoutePlannerService, reservationService,
                new MapfReservationMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    /**
     * 单位契约（路线图 §7.2「MAPF 单位不一致」）。
     *
     * <p>以前的算法：把示意 px 当米用，再除以动画速度 8 px/s。本 seed 上 px→米是**各向异性**的
     * （横向 1.2263、纵向 0.7390），所以误差按航向摆 —— 横向边只覆盖真实占位时间的 30/80 = 37.5%，
     * 纵向边 25/40 = 62.5%。这两个数字就是本条修复的验收点。
     */
    @Test
    void reservationWindowShouldBeMetresOverMetresPerSecondAndAnisotropyAware() {
        MapfProperties properties = new MapfProperties();
        properties.setEnabled(true);
        properties.setBucketMs(500L);
        properties.setVehicleSpeedMetersPerSecond(3.66D);
        MapfReservationService spy = org.mockito.Mockito.mock(MapfReservationService.class);
        when(spy.isEnabled()).thenReturn(true);
        when(spy.currentBucket()).thenReturn(0L);
        when(spy.tryReserveEdge(any(), any(), any(), any(), any(Long.class))).thenReturn(true);
        MapfRoutePlannerService service = new MapfRoutePlannerService(
                properties, parkRoutePlannerService, spy,
                new MapfReservationMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        // R1→R2 纯横向 120 px，R2→R5 纯纵向 100 px；都没有 GPS，正好走 px→米的换算分支
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(
                List.of(node("R1", 100, 120), node("R2", 220, 120), node("R5", 220, 220)),
                List.of(segment("R1", "R2"), segment("R2", "R5")));
        when(parkRoutePlannerService.loadGraph(1L)).thenReturn(graph);
        when(parkRoutePlannerService.shortestNodePathWithPenalties(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("R1", "R2", "R5"));
        when(parkRoutePlannerService.buildRouteFromNodePath(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(ParkPointResponse.builder().code("R5").build()));

        service.planAndReserve(1L, 10L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(225), BigDecimal.valueOf(225));

        ArgumentCaptor<Long> cursors = ArgumentCaptor.forClass(Long.class);
        org.mockito.Mockito.verify(spy, org.mockito.Mockito.times(2))
                .tryReserveEdge(any(), any(), any(), any(), cursors.capture());
        List<Long> seen = cursors.getAllValues();
        assertEquals(0L, seen.get(0), "第一条边从当前桶开始");
        // 横向 120 px = 147.16 m → 40.2 s → 80 个 500ms 桶；纵向 100 px = 73.90 m → 20.2 s → 40 桶
        assertEquals(80L, seen.get(1), "横向边的桶数必须按米算：" + seen);

        double horizontalCoverage = 120D / 8D / 0.5 / 80D;   // 旧算法在同一条件下的桶数比
        assertEquals(0.375D, horizontalCoverage, 1e-9, "旧口径只覆盖 37.5% 的占位时间，这条钉住 bug 的量级");
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
