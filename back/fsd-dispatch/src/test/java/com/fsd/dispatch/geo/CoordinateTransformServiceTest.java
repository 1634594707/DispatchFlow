package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateTransformServiceTest {

    @Test
    void roundTripsParkAndGcj02() {
        ParkPilotProperties properties = new ParkPilotProperties();
        ParkGeoTransformService geoTransformService = new ParkGeoTransformService(properties);
        CoordinateTransformService service = new CoordinateTransformService(
                properties,
                geoTransformService,
                null);

        var forward = service.parkToGcj02(new BigDecimal("600"), new BigDecimal("400"));
        assertTrue(forward.isPresent());

        var backward = service.gcj02ToPark(forward.get().getLongitude(), forward.get().getLatitude());
        assertTrue(backward.isPresent());
        assertEquals(new BigDecimal("600.0000"), backward.get().getParkX());
        assertEquals(new BigDecimal("400.0000"), backward.get().getParkY());
    }
}
