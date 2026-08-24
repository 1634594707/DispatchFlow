package com.fsd.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.admin.vo.RoadRouteValidateRequest;
import com.fsd.admin.vo.RoadRouteValidateResponse;
import com.fsd.dispatch.entity.MapDataVersionEntity;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RouteAuditService;
import com.fsd.dispatch.geo.RouteEndpointSnapper;
import com.fsd.dispatch.geo.RouteMetricsCalculator;
import com.fsd.dispatch.mapper.MapDataVersionMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 路线图 5.2：路线执行契约 ——
 * <ol>
 *   <li>路线执行绑定 routeId / mapVersion / source / segmentPath；</li>
 *   <li>失败（吸附超限、直线回退被禁）返回明确原因编码与详情；</li>
 *   <li>订单目标点必须吸附到道路接入点（snapDistanceMeters 记录吸附距离）。</li>
 * </ol>
 */
class RoadRouteContractTest {

    private RoadRouteService roadRouteService;
    private RoadRouteCollisionValidator collisionValidator;
    private RouteMetricsCalculator routeMetricsCalculator;
    private RouteEndpointSnapper endpointSnapper;
    private RouteAuditService routeAuditService;
    private MapDataVersionMapper mapDataVersionMapper;
    private RoadRouteValidateAdminService service;

    @BeforeEach
    void setUp() {
        roadRouteService = mock(RoadRouteService.class);
        collisionValidator = mock(RoadRouteCollisionValidator.class);
        routeMetricsCalculator = mock(RouteMetricsCalculator.class);
        endpointSnapper = mock(RouteEndpointSnapper.class);
        routeAuditService = mock(RouteAuditService.class);
        mapDataVersionMapper = mock(MapDataVersionMapper.class);
        service = new RoadRouteValidateAdminService(roadRouteService, collisionValidator,
                routeMetricsCalculator, endpointSnapper, routeAuditService,
                null, mapDataVersionMapper);
    }

    private RoadRouteValidateRequest request() {
        RoadRouteValidateRequest request = new RoadRouteValidateRequest();
        request.setOriginLng(new BigDecimal("121.100000"));
        request.setOriginLat(new BigDecimal("31.920000"));
        request.setDestinationLng(new BigDecimal("121.110000"));
        request.setDestinationLat(new BigDecimal("31.925000"));
        request.setParkId(1L);
        request.setSnapDistanceMeters(50D);
        return request;
    }

    private GeoPoint point(String lng, String lat) {
        return new GeoPoint(new BigDecimal(lng), new BigDecimal(lat));
    }

    @Test
    void successfulPlanShouldBindContractFieldsAndSavePlannedAudit() {
        RoadRouteValidateRequest request = request();
        request.setMapVersion("V43");

        when(endpointSnapper.snapToRoadNode(any(), eq(1L), isNull(), eq(50D)))
                .thenReturn(new RouteEndpointSnapper.SnapResult("N-001", point("121.100100", "31.920010"), 3.5, true))
                .thenReturn(new RouteEndpointSnapper.SnapResult("N-009", point("121.109900", "31.924990"), 4.5, true));
        RoadRouteResult planned = new RoadRouteResult(
                List.of(point("121.100100", "31.920010"), point("121.109900", "31.924990")),
                850D, com.fsd.dispatch.geo.RoadRouteSource.LOCAL_GRAPH);
        when(roadRouteService.planDrivingRoute(any(), any())).thenReturn(planned);
        when(collisionValidator.applyValidation(any(), any())).thenReturn(planned);
        when(routeMetricsCalculator.compute(isNull(), anyList(), anyList(), isNull(), isNull(), isNull()))
                .thenReturn(new com.fsd.dispatch.geo.RouteMetrics(850D, 120L, 0L, 0L, List.of(), null, null));

        RoadRouteValidateResponse response = service.validate(request);

        // 路线执行绑定契约：routeId / mapVersion / source / segmentPath 全部非空且一致
        assertNotNull(response.getRouteId());
        assertTrue(response.getRouteId().startsWith("ROUTE-"));
        assertEquals("V43", response.getMapVersion());
        assertEquals("LOCAL_GRAPH", response.getSource());
        assertEquals(List.of("N-001>N-009"), response.getSegmentPath());
        assertFalse(response.isInvalid());
        assertNull(response.getUnreachableReason());
        assertEquals(8.0, response.getSnapDistanceMeters(), 0.001);

        // 审计记录保存为 PLANNED 且带地图版本
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(routeAuditService).saveRouteAudit(
                eq(response.getRouteId()), eq(1L), isNull(), eq("V43"), eq("REAL_ROAD"),
                eq("LOCAL_GRAPH"), any(), any(), anyString(), any(),
                eq(true), eq(false), eq(false), isNull(),
                statusCaptor.capture(), isNull(), isNull());
        assertEquals("PLANNED", statusCaptor.getValue());
    }

    @Test
    void startOffRoadShouldReturnExplicitReasonWithoutPlanning() {
        RoadRouteValidateRequest request = request();
        when(endpointSnapper.snapToRoadNode(any(), eq(1L), isNull(), eq(50D)))
                .thenReturn(new RouteEndpointSnapper.SnapResult("N-X", point("121.2", "31.99"), 300D, false));

        RoadRouteValidateResponse response = service.validate(request);

        assertTrue(response.isInvalid());
        assertEquals(com.fsd.dispatch.geo.RouteUnreachableReason.START_OFF_ROAD.code(),
                response.getUnreachableReason());
        assertNotNull(response.getUnreachableDetail());
        assertEquals(List.of(), response.getSegmentPath());
        org.mockito.Mockito.verifyNoInteractions(roadRouteService);
    }

    @Test
    void disabledStraightLineFallbackShouldBeInvalidWithReason() {
        RoadRouteValidateRequest request = request();
        request.setAllowStraightLine(false);

        when(endpointSnapper.snapToRoadNode(any(), eq(1L), isNull(), eq(50D)))
                .thenReturn(new RouteEndpointSnapper.SnapResult("N-001", point("121.1", "31.92"), 1D, true))
                .thenReturn(new RouteEndpointSnapper.SnapResult("N-009", point("121.11", "31.925"), 1D, true));
        // 规划器只能给出直线回退路线
        RoadRouteResult straightLine = new RoadRouteResult(
                List.of(point("121.1", "31.92"), point("121.11", "31.925")),
                900D, com.fsd.dispatch.geo.RoadRouteSource.STRAIGHT_LINE);
        when(roadRouteService.planDrivingRoute(any(), any())).thenReturn(straightLine);
        when(collisionValidator.applyValidation(any(), any())).thenReturn(straightLine);
        when(routeMetricsCalculator.compute(isNull(), anyList(), anyList(), isNull(), isNull(), isNull()))
                .thenReturn(new com.fsd.dispatch.geo.RouteMetrics(900D, 130L, 0L, 0L, List.of(), null, null));

        RoadRouteValidateResponse response = service.validate(request);

        assertTrue(response.isInvalid());
        assertEquals(com.fsd.dispatch.geo.RouteUnreachableReason.NO_PATH_ON_GRAPH.code(),
                response.getUnreachableReason());
        assertTrue(response.getUnreachableDetail().contains("allowStraightLine=false"));
    }
}