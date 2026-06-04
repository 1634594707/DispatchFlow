package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.local.OsmPilotGeoTestSupport;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import com.fsd.dispatch.geo.local.PilotForbiddenZones;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRouteCollisionValidatorTest {

    private RoadRouteCollisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = OsmPilotGeoTestSupport.validator();
    }

    @Test
    void localGraphRouteShouldPassValidation() {
        GeoPoint idle = PilotForbiddenZones.g(121.080354, 31.961977);
        GeoPoint drop04 = PilotForbiddenZones.g(121.084000, 31.961977);
        List<GeoPoint> polyline = OsmPilotGeoTestSupport.graph()
                .planDrivingRoute(idle, drop04)
                .polyline();

        RoadRouteValidation result = validator.validate(polyline);

        assertFalse(result.invalid(), "Mid-row corridor route should not cross OSM buildings");
    }

    @Test
    void straightLineThroughBuildingShouldBeInvalid() {
        GeoPoint southWest = PilotForbiddenZones.g(121.073500, 31.961000);
        GeoPoint mid1 = PilotForbiddenZones.g(121.074500, 31.961400);
        GeoPoint mid2 = PilotForbiddenZones.g(121.076000, 31.961900);
        GeoPoint northEast = PilotForbiddenZones.g(121.077500, 31.962400);
        List<GeoPoint> diagonal = List.of(southWest, mid1, mid2, northEast);

        RoadRouteValidation result = validator.validate(diagonal);

        assertTrue(result.invalid());
        assertTrue(result.crossesBuilding());
    }

    @Test
    void shortPolylineShouldBeInvalid() {
        GeoPoint a = PilotForbiddenZones.g(121.075160, 31.960424);
        GeoPoint b = PilotForbiddenZones.g(121.079152, 31.963523);
        RoadRouteValidation result = validator.validate(List.of(a, b));

        assertTrue(result.invalid());
    }

    @Test
    void serviceYardCrossingShouldBeInvalid() {
        GeoPoint west = PilotForbiddenZones.g(121.075500, 31.961900);
        GeoPoint beforeYard = PilotForbiddenZones.g(121.076200, 31.962000);
        GeoPoint inYard = PilotForbiddenZones.g(121.077000, 31.962350);
        GeoPoint afterYard = PilotForbiddenZones.g(121.078500, 31.962000);
        List<GeoPoint> acrossYard = List.of(west, beforeYard, inYard, afterYard);

        RoadRouteValidation result = validator.validate(acrossYard);

        assertTrue(result.invalid());
        assertTrue(result.crossesRiver());
    }

    @Test
    void expandedWarehouseRoutesShouldPassValidation() {
        LocalPilotRoadGraphService graph = OsmPilotGeoTestSupport.graph();
        GeoPoint idle = PilotForbiddenZones.g(121.080354, 31.961977);
        GeoPoint drop04 = PilotForbiddenZones.g(121.084000, 31.961977);
        GeoPoint drop02 = PilotForbiddenZones.g(121.088022, 31.961825);

        RoadRouteValidation idleToDrop04 = validator.validate(graph.planDrivingRoute(idle, drop04).polyline());
        RoadRouteValidation drop04ToDrop02 = validator.validate(graph.planDrivingRoute(drop04, drop02).polyline());

        assertFalse(idleToDrop04.invalid(), "IDLE → DROP04 should follow mid-row corridor");
        assertFalse(drop04ToDrop02.invalid(), "DROP04 → DROP02 should follow east corridor");
    }
}
