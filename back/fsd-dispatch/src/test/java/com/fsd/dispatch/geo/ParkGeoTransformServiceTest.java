package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.ParkPilotProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ParkGeoTransformServiceTest {

    @Test
    void toGcj02_mapsParkCenterToAnchor() {
        ParkPilotProperties properties = new ParkPilotProperties();
        ParkGeoTransformService service = new ParkGeoTransformService(properties);

        var geo = service.toGcj02(new BigDecimal("600"), new BigDecimal("400"));

        assertTrue(geo.isPresent());
        assertEquals(new BigDecimal("121.080354"), geo.get().longitude());
        assertEquals(new BigDecimal("31.961977"), geo.get().latitude());
    }

    @Test
    void fromGcj02_roundTripsParkCoordinates() {
        ParkPilotProperties properties = new ParkPilotProperties();
        ParkGeoTransformService service = new ParkGeoTransformService(properties);

        var park = service.fromGcj02(new BigDecimal("121.080354"), new BigDecimal("31.961977"));

        assertTrue(park.isPresent());
        assertEquals(0, park.get().x().compareTo(new BigDecimal("600.000000")));
        assertEquals(0, park.get().y().compareTo(new BigDecimal("400.000000")));
    }
}
