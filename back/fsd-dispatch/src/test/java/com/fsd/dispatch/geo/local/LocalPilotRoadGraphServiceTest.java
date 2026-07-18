package com.fsd.dispatch.geo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LocalPilotRoadGraphServiceTest {

    private final OsmPilotGeoRepository repository = mock(OsmPilotGeoRepository.class);

    @Test
    void baseRoutesStartAtBaseAndLeaveThroughRn27() {
        when(repository.isLoaded()).thenReturn(false);
        LocalPilotRoadGraphService service = new LocalPilotRoadGraphService(repository);
        GeoPoint base = point(121.080681, 31.960337);
        GeoPoint pickup = point(121.074453, 31.960396);

        var route = service.planDrivingRoute(base, pickup);

        assertEquals(base, route.polyline().get(0));
        assertEquals(point(121.081550, 31.960600), route.polyline().get(1));
    }

    @Test
    void idleAnchorMatchesThePhysicalBase() {
        LocalPilotRoadGraphService service = new LocalPilotRoadGraphService(repository);

        assertEquals(point(121.080681, 31.960337), service.getStationAnchors().get("IDLE01"));
    }

    private static GeoPoint point(double longitude, double latitude) {
        return new GeoPoint(BigDecimal.valueOf(longitude).setScale(6),
                BigDecimal.valueOf(latitude).setScale(6));
    }
}
