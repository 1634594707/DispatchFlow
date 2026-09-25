package com.fsd.dispatch.geo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteSource;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * W2-e：路线规划源换成现役 DB 路网之后的行为契约。
 *
 * <p>重点守两件事：① 沿真实几何的折线要带中间顶点；② **算不出来时必须返回 null**，
 * 绝不能给一条两端直线冒充"沿实路"—— 那正是路线图 §7 明令禁止的静默兜底。
 */
class DbRoadGraphRouteServiceTest {

    private static final GeoPoint ORIGIN = new GeoPoint(bd("121.0800"), bd("31.9600"));
    private static final GeoPoint DEST = new GeoPoint(bd("121.0900"), bd("31.9650"));

    private ParkRoutePlannerService planner;
    private ParkGeoTransformService transform;
    private ParkPilotProperties properties;
    private DbRoadGraphRouteService service;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @BeforeEach
    void setUp() {
        planner = mock(ParkRoutePlannerService.class);
        transform = mock(ParkGeoTransformService.class);
        properties = new ParkPilotProperties();
        service = new DbRoadGraphRouteService(planner, transform, properties);
        // 示意坐标的具体值不参与判定，给个稳定的即可；真正的判据在"折线带不带中间顶点"上
        when(transform.fromGcj02(any(), any())).thenAnswer(inv -> Optional.of(
                new ParkGeoTransformService.ParkPoint(bd("100"), bd("200"))));
        when(planner.isReachable(any(), any(), any(), any(), any())).thenReturn(true);
    }

    private static ParkPointResponse pt(String code, String lng, String lat) {
        return ParkPointResponse.builder().code(code)
                .x(bd("10")).y(bd("20"))
                .longitude(lng == null ? null : bd(lng))
                .latitude(lat == null ? null : bd(lat)).build();
    }

    @Test
    void keepsEveryIntermediateVertexFromTheGraphPolyline() {
        when(planner.buildRoute(any(), any(), any(), any(), any())).thenReturn(List.of(
                pt("START", null, null),
                pt("OSM0017", "121.0800", "31.9600"),
                pt("OSM0017>OSM0120#1", "121.0835", "31.9620"),   // 边折线的中间形状点
                pt("OSM0120", "121.0870", "31.9640"),
                pt("END", null, null)));

        RoadRouteResult result = service.plan(ORIGIN, DEST);

        assertNotNull(result);
        assertEquals(RoadRouteSource.LOCAL_GRAPH, result.source());
        // ORIGIN + 中间形状点 + OSM0120 + DEST = 4：图上第一个顶点与 ORIGIN 同点，被合并掉了，
        // 而**中间形状点必须留着** —— 那才是这条改动的全部内容。
        assertEquals(4, result.polyline().size(), "边折线的中间形状点必须留在折线里");
        assertEquals(0, new BigDecimal("121.0835").compareTo(result.polyline().get(1).longitude()));
        assertEquals(0, new BigDecimal("121.0900").compareTo(result.polyline().get(3).longitude()));
        assertTrue(result.distanceMeters() > 0D);
    }

    @Test
    void refusesToFakeARouteWhenTheGraphYieldsNoIntermediateVertex() {
        when(planner.buildRoute(any(), any(), any(), any(), any())).thenReturn(List.of(
                pt("START", null, null), pt("END", null, null)));

        assertNull(service.plan(ORIGIN, DEST),
                "只剩两端就是一条直线 —— 必须交回调用方，不能冒充沿实路的结果（§7）");
    }

    @Test
    void returnsNullWhenTheTwoEndsAreNotConnectedOnTheGraph() {
        when(planner.isReachable(any(), any(), any(), any(), any())).thenReturn(false);

        assertNull(service.plan(ORIGIN, DEST));
        verify(planner, never()).buildRoute(any(), any(), any(), any(), any());
    }

    @Test
    void dropsConsecutiveDuplicateVerticesThatWouldWasteAFollowerFrame() {
        when(planner.buildRoute(any(), any(), any(), any(), any())).thenReturn(List.of(
                pt("START", null, null),
                pt("OSM0001", "121.0800", "31.9600"),   // 与 ORIGIN 同点
                pt("OSM0002", "121.0870", "31.9640"),
                pt("OSM0002b", "121.0870", "31.9640"),  // 重复
                pt("END", null, null)));

        RoadRouteResult result = service.plan(ORIGIN, DEST);

        assertNotNull(result);
        assertEquals(3, result.polyline().size(), "相邻重复点要合并");
    }

    @Test
    void switchOffDisablesTheWholePathAndReportsIt() {
        properties.getGeo().setRouteSourceFromGraph(false);
        assertTrue(service.isEnabled() == false);
    }

    /** 接线本身也要有证据：不设 setter 时旧行为逐字不变，设了才走 DB 图。 */
    @Test
    void localGraphServiceDelegatesToTheDbGraphOnlyWhenItIsWiredIn() {
        LocalPilotRoadGraphService legacy = new LocalPilotRoadGraphService(OsmPilotGeoTestSupport.repository());
        RoadRouteResult withoutWiring = legacy.planDrivingRoute(ORIGIN, DEST);
        assertTrue(withoutWiring.distanceMeters() != 1234D,
                "没接线时不该出现 DB 图的结果（旧行为逐字不变）");

        DbRoadGraphRouteService stub = mock(DbRoadGraphRouteService.class);
        when(stub.isEnabled()).thenReturn(true);
        when(stub.plan(any(), any())).thenReturn(new RoadRouteResult(List.of(
                ORIGIN, new GeoPoint(bd("121.0835"), bd("31.9620")), DEST), 1234D, RoadRouteSource.LOCAL_GRAPH));
        legacy.setDbRoadGraphRouteService(stub);

        RoadRouteResult viaDb = legacy.planDrivingRoute(ORIGIN, DEST);
        assertEquals(3, viaDb.polyline().size());
        assertEquals(1234D, viaDb.distanceMeters(), 1e-9);
        verify(stub).plan(ORIGIN, DEST);
    }

    @Test
    void localGraphServiceFallsBackWhenTheDbGraphCannotProduceARoute() {
        LocalPilotRoadGraphService legacy = new LocalPilotRoadGraphService(OsmPilotGeoTestSupport.repository());
        DbRoadGraphRouteService stub = mock(DbRoadGraphRouteService.class);
        when(stub.isEnabled()).thenReturn(true);
        when(stub.plan(any(), any())).thenReturn(null);
        legacy.setDbRoadGraphRouteService(stub);

        RoadRouteResult result = legacy.planDrivingRoute(ORIGIN, DEST);
        assertNotNull(result, "DB 图算不出来要退回老路径，而不是把这一跳丢掉");
    }
}
