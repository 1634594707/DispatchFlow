package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkGeofenceResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 任意点下单的受理判据。
 *
 * <p>重点盯的是"看起来吸附成功、其实派不出去"那一类：现役 446 个 ACTIVE 节点里有 29 个
 * 不在最大强连通分量上，落在孤立点旁边的坐标必须被拒，而不是静默吸附过去。
 */
class OrderEndpointResolverTest {

    private static final long PARK_ID = 7L;

    private ParkRoutePlannerService planner;
    private ParkStationService stationService;
    private StationMapper stationMapper;
    private ParkGeofenceService geofenceService;
    private OrderEndpointResolver resolver;
    private final AtomicLong stationSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        planner = mock(ParkRoutePlannerService.class);
        stationService = mock(ParkStationService.class);
        stationMapper = mock(StationMapper.class);
        geofenceService = mock(ParkGeofenceService.class);
        ParkPilotProperties properties = new ParkPilotProperties();
        resolver = new OrderEndpointResolver(planner, geofenceService, stationService, stationMapper, properties);

        // N1 <-> N2 连通；N3 孤立（模拟换图后掉出最大强连通分量的那批节点）。
        when(planner.loadGraph(PARK_ID)).thenReturn(graph());
        when(planner.isReachable(any(), any(), any(), any(), any())).thenAnswer(call -> {
            BigDecimal ax = call.getArgument(1);
            BigDecimal ay = call.getArgument(2);
            BigDecimal bx = call.getArgument(3);
            BigDecimal by = call.getArgument(4);
            return sameNode(ax, ay, "N1", "N2") && sameNode(bx, by, "N1", "N2");
        });
        when(geofenceService.listActiveByPark(PARK_ID)).thenReturn(List.of(serviceFence()));
        when(stationMapper.selectOne(any())).thenReturn(null);
        when(stationMapper.insert(any(StationEntity.class))).thenAnswer(call -> {
            StationEntity entity = call.getArgument(0);
            entity.setId(stationSeq.incrementAndGet());
            return 1;
        });
        when(stationService.requireStation(any())).thenAnswer(call ->
                ParkStationResponse.builder()
                        .stationId(call.getArgument(0))
                        .parkId(PARK_ID)
                        .stationCode("ZJF-TEST")
                        .x(BigDecimal.valueOf(500))
                        .y(BigDecimal.valueOf(500))
                        .coordLng(BigDecimal.valueOf(121.0805))
                        .coordLat(BigDecimal.valueOf(31.96))
                        .build());
    }

    private static boolean sameNode(BigDecimal x, BigDecimal y, String left, String right) {
        double vx = x.doubleValue();
        double vy = y.doubleValue();
        return (left.equals("N1") && Math.abs(vx - 100) < 1 && Math.abs(vy - 100) < 1)
                || (right.equals("N2") && Math.abs(vx - 200) < 1 && Math.abs(vy - 100) < 1);
    }

    private static ParkRoadGraph graph() {
        List<RoadNodeEntity> nodes = new ArrayList<>();
        nodes.add(node("N1", 100, 100, 121.0800, 31.9600));
        nodes.add(node("N2", 200, 100, 121.0810, 31.9600));
        nodes.add(node("N3", 900, 900, 121.1000, 31.9900));
        List<RoadSegmentEntity> segments = List.of(segment("N1", "N2"));
        return ParkRoadGraph.fromDatabase(nodes, segments);
    }

    private static RoadNodeEntity node(String code, double x, double y, double lng, double lat) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setParkId(PARK_ID);
        entity.setNodeCode(code);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setCoordLng(BigDecimal.valueOf(lng));
        entity.setCoordLat(BigDecimal.valueOf(lat));
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setParkId(PARK_ID);
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setDirection("BIDIRECTIONAL");
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return entity;
    }

    /** ACTIVE + ZJF-ZONE- 前缀才会被算进可派单并集（ParkGeofenceServiceImpl 同一判据）。 */
    private static ParkGeofenceResponse serviceFence() {
        return ParkGeofenceResponse.builder()
                .fenceCode("ZJF-ZONE-TEST")
                .status("ACTIVE")
                .dispatchable(true)
                .polygon(List.of(
                        List.of(BigDecimal.valueOf(121.070), BigDecimal.valueOf(31.950)),
                        List.of(BigDecimal.valueOf(121.110), BigDecimal.valueOf(31.950)),
                        List.of(BigDecimal.valueOf(121.110), BigDecimal.valueOf(31.995)),
                        List.of(BigDecimal.valueOf(121.070), BigDecimal.valueOf(31.995))))
                .build();
    }

    @Test
    void stationToEndpointsKeepsTheLegacyPathUntouched() {
        assertNull(resolver.resolveForAcceptance(PARK_ID, 11L, null, null, 12L, null, null));
    }

    @Test
    void snapsBothCoordinatesOntoTheConnectedGraphAndRegistersThem() {
        OrderEndpointResolver.Accepted accepted = resolver.resolveForAcceptance(
                PARK_ID, null, bd(121.0800), bd(31.9600),
                null, bd(121.0810), bd(31.9600));

        assertNotNull(accepted);
        assertNotNull(accepted.pickupStationId());
        assertNotNull(accepted.dropoffStationId());
        assertTrue(accepted.pickupStationId() < accepted.dropoffStationId(),
                "两端应各自登记成不同的点");
        assertEquals("N1", accepted.auditResolution().pickupNodeCode());
        assertEquals("N2", accepted.auditResolution().dropoffNodeCode());
        assertNotNull(accepted.auditResolution().pickupSnapMeters());
    }

    @Test
    void rejectsCoordinatesOutsideEveryDispatchableFence() {
        BusinessException error = assertThrows(BusinessException.class, () -> resolver.resolveForAcceptance(
                PARK_ID, null, bd(121.2000), bd(31.9900),
                null, bd(121.0810), bd(31.9600)));
        assertEquals("ORDER_ENDPOINT_OUT_OF_SERVICE_AREA", error.getCode());
    }

    @Test
    void rejectsWhenNoGraphNodeIsWithinSnapRadius() {
        // 在范围内（围栏覆盖）但离任何节点都超过 250 m —— 这就是"图框里那一半没有路"的地方。
        BusinessException error = assertThrows(BusinessException.class, () -> resolver.resolveForAcceptance(
                PARK_ID, null, bd(121.0790), bd(31.9560),
                null, bd(121.0810), bd(31.9600)));
        assertEquals("ORDER_ENDPOINT_SNAP_FAILED", error.getCode());
    }

    @Test
    void refusesToSnapOntoANodeThatCannotReachTheOtherEnd() {
        // N3 在图上但没有任何边：吸附到它等于接一张永远派不出去的死单。
        BusinessException error = assertThrows(BusinessException.class, () -> resolver.resolveForAcceptance(
                PARK_ID, null, bd(121.0800), bd(31.9600),
                null, bd(121.1000), bd(31.9900)));
        assertEquals("ORDER_ENDPOINT_UNREACHABLE", error.getCode());
    }

    @Test
    void reusesTheRegisteredPointForTheSameNodeInsteadOfInsertingAgain() {
        StationEntity existing = new StationEntity();
        existing.setId(4242L);
        existing.setStationCode("GEO-N1");
        existing.setStatus("ACTIVE");
        existing.setDeleted(0);
        when(stationMapper.selectOne(any())).thenReturn(existing);

        OrderEndpointResolver.Accepted accepted = resolver.resolveForAcceptance(
                PARK_ID, null, bd(121.0800), bd(31.9600),
                null, bd(121.0810), bd(31.9600));

        assertEquals(4242L, accepted.pickupStationId());
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
