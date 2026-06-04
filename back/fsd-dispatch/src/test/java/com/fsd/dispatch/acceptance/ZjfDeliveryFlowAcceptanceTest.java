package com.fsd.dispatch.acceptance;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteValidation;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import com.fsd.dispatch.geo.local.OsmPilotGeoTestSupport;
import com.fsd.dispatch.geo.local.PilotForbiddenZones;
import com.fsd.dispatch.geo.local.StationCoordinateValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 叠石桥 L1 下单链路验收：站点坐标 + 取送货道路径不穿建筑。
 */
@DisplayName("ZJF 移动下单路线验收")
class ZjfDeliveryFlowAcceptanceTest {

    private static LocalPilotRoadGraphService graph;
    private static RoadRouteCollisionValidator routeValidator;
    private static StationCoordinateValidator stationValidator;

    @BeforeAll
    static void init() {
        graph = OsmPilotGeoTestSupport.graph();
        routeValidator = OsmPilotGeoTestSupport.validator();
        stationValidator = OsmPilotGeoTestSupport.stationValidator();
    }

    @ParameterizedTest(name = "站点 {0} 坐标合法")
    @CsvSource({
            "ZJF-PICK-01, 121.074453, 31.960396",
            "ZJF-PICK-02, 121.072610, 31.960726",
            "ZJF-DROP-01, 121.079762, 31.963627",
            "ZJF-DROP-02, 121.087005, 31.961780",
            "ZJF-DROP-03, 121.074367, 31.963548",
            "ZJF-DROP-04, 121.083893, 31.962833",
            "ZJF-EXPRESS-01, 121.072610, 31.960726",
            "ZJF-IDLE-01, 121.080055, 31.961922",
    })
    void pilotStationsShouldBeValid(String code, double lng, double lat) {
        StationCoordinateValidator.ValidationResult result = stationValidator.validate(lng, lat);
        assertTrue(result.valid(), code + ": " + result.summary());
    }

    @ParameterizedTest(name = "{0}: {1} → {2}")
    @CsvSource({
            "门市A→代发仓, ZJF-PICK-01, 121.074453, 31.960396, ZJF-DROP-01, 121.079762, 31.963627",
            "门市B→代发仓, ZJF-PICK-02, 121.072610, 31.960726, ZJF-DROP-01, 121.079762, 31.963627",
            "代拿仓→代发仓, ZJF-DROP-02, 121.087005, 31.961780, ZJF-DROP-01, 121.079762, 31.963627",
            "代发仓→快递, ZJF-DROP-01, 121.079762, 31.963627, ZJF-EXPRESS-01, 121.072610, 31.960726",
            "门市A→西排北仓, ZJF-PICK-01, 121.074453, 31.960396, ZJF-DROP-03, 121.074367, 31.963548",
            "门市A→东排集散, ZJF-PICK-01, 121.074453, 31.960396, ZJF-DROP-04, 121.083893, 31.962833",
            "西排北仓→代发仓, ZJF-DROP-03, 121.074367, 31.963548, ZJF-DROP-01, 121.079762, 31.963627",
            "车队待命→东排集散, ZJF-IDLE-01, 121.080055, 31.961922, ZJF-DROP-04, 121.083893, 31.962833",
    })
    void mobileDemoRoutesShouldFollowRoadsNotBuildings(String label,
                                                     String fromCode, double fromLng, double fromLat,
                                                     String toCode, double toLng, double toLat) {
        GeoPoint from = PilotForbiddenZones.g(fromLng, fromLat);
        GeoPoint to = PilotForbiddenZones.g(toLng, toLat);
        RoadRouteResult route = graph.planDrivingRoute(from, to);

        assertFalse(route.polyline().isEmpty(),
                label + ": 应能规划出路网路径 (" + fromCode + " → " + toCode + "), source=" + route.source());
        assertTrue(route.polyline().size() >= 4,
                label + ": 路径点过少，可能仍为直线兜底, points=" + route.polyline().size());
        assertFalse(route.invalid(),
                label + ": 规划结果标记为 invalid");

        RoadRouteResult afterCollision = routeValidator.applyValidation(route);
        assertTrue(afterCollision.polyline().size() >= 4,
                label + ": 碰撞校验后路径被清空, points=" + afterCollision.polyline().size());
        assertFalse(afterCollision.invalid(),
                label + ": 碰撞校验后标记 invalid");

        RoadRouteValidation validation = routeValidator.validate(route.polyline());
        assertFalse(validation.invalid(),
                label + ": 路径穿越建筑/水域 (building=" + validation.crossesBuilding()
                        + ", river=" + validation.crossesRiver() + ")");
        assertFalse(validation.crossesBuilding(),
                label + ": 穿越 OSM 建筑 footprint");
    }

    @Test
    void straightLineBetweenPickupAndDropShouldBeRejected() {
        GeoPoint pick = PilotForbiddenZones.g(121.074453, 31.960396);
        GeoPoint drop = PilotForbiddenZones.g(121.079762, 31.963627);
        RoadRouteValidation diagonal = routeValidator.validate(java.util.List.of(pick, drop));

        assertTrue(diagonal.invalid(), "两点直线应被判为非法");
    }
}
