package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * W2-b：路线门禁。守三件事 —— 判什么、不判什么、以及**默认绝不动行为**。
 *
 * <p>"不判什么"这一半比"判什么"更要紧：off-road 那项参照的几何只有 3.195 km²
 * （{@code pilot_osm_geo.json} 的 30 条折线，§13.88），把它当禁行会把服务范围里
 * 绝大部分真路线判死、等于关掉派单。所以这里有专门一条断言钉住"off-road 不算拒"。
 */
class RoadRouteGateTest {

    private ParkPilotProperties properties;
    private SimpleMeterRegistry registry;
    private RoadRouteGate gate;

    private static GeoPoint p(String lng, String lat) {
        return new GeoPoint(new BigDecimal(lng), new BigDecimal(lat));
    }

    private static RoadRouteResult road(int vertices) {
        java.util.List<GeoPoint> line = new java.util.ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            line.add(p(String.valueOf(121.08 + i * 0.001), String.valueOf(31.96 + i * 0.001)));
        }
        return new RoadRouteResult(line, 1000D, RoadRouteSource.LOCAL_GRAPH);
    }

    @BeforeEach
    void setUp() {
        properties = new ParkPilotProperties();
        registry = new SimpleMeterRegistry();
        gate = new RoadRouteGate(registry, properties);
    }

    @Test
    void defaultConfigurationJudgesNothingAndChangesNoBehaviour() {
        assertFalse(properties.getGeo().isRouteGateEnabled(), "默认必须关着：拒单是产品行为，不能顺手打开");
        assertFalse(properties.getGeo().isRouteGateRejects());

        RoadRouteResult straight = new RoadRouteResult(List.of(p("121.08", "31.96"), p("121.09", "31.97")),
                500D, RoadRouteSource.STRAIGHT_LINE);
        RoadRouteGate.Verdict verdict = gate.evaluate(straight, "test");

        assertFalse(verdict.rejected(), "开关关着时哪怕原因码已识别出来，也不许撤线");
        assertEquals("ROUTE_SOURCE_STRAIGHT_LINE", verdict.reasonCode(), "但原因码要先量得到，决定才有数可依");
    }

    @Test
    void emptyPolylineIsTheForbiddenFallbackItClaimsToBe() {
        assertEquals("ROUTE_GEOMETRY_EMPTY",
                RoadRouteGate.reasonCodeOf(new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE)));
    }

    @Test
    void offRoadAloneNeverRejectsBecauseItsReferenceGeometryOnlyCovers3KmSquared() {
        // invalid=true 但既不是直线来源、也没压建筑：这就是那 30 条折线判出来的"离路"
        RoadRouteResult offRoad = new RoadRouteResult(
                List.of(p("121.080", "31.960"), p("121.085", "31.962"), p("121.090", "31.965"), p("121.095", "31.968")),
                1500D, RoadRouteSource.LOCAL_GRAPH,
                true, false, false, 999D);
        properties.getGeo().setRouteGateEnabled(true);
        properties.getGeo().setRouteGateRejects(true);

        assertNull(RoadRouteGate.reasonCodeOf(offRoad), "off-road 不得进全范围判据（§13.88）");
        assertFalse(gate.evaluate(offRoad, "test").rejected());
    }

    @Test
    void crossingABuildingRejectsOnceBothKeysAreTurned() {
        RoadRouteResult overBuilding = road(5).withValidation(new RoadRouteValidation(false, true, false, 10D));
        assertNull(RoadRouteGate.reasonCodeOf(road(5)));
        assertEquals("ROUTE_CROSSES_BUILDING", RoadRouteGate.reasonCodeOf(overBuilding));

        properties.getGeo().setRouteGateEnabled(true);
        properties.getGeo().setRouteGateRejects(true);
        assertTrue(gate.evaluate(overBuilding, "test").rejected());
    }

    @Test
    void metricsRecordBothSidesSoTheRejectionDecisionCanBeMadeOnNumbers() {
        properties.getGeo().setRouteGateEnabled(true);
        gate.evaluate(road(6), "test");                                  // pass
        gate.evaluate(road(4).withValidation(new RoadRouteValidation(false, true, false, 5D)), "test"); // building

        assertEquals(1D, registry.get("dispatchflow.route.gate").tag("outcome", "pass").counter().count());
        assertEquals(1D, registry.get("dispatchflow.route.gate")
                .tag("outcome", "ROUTE_CROSSES_BUILDING").counter().count());
    }

    @Test
    void missingResultIsItsOwnReasonRatherThanAnNpe() {
        assertEquals("ROUTE_RESULT_MISSING", RoadRouteGate.reasonCodeOf(null));
    }
}
