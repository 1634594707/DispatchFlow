package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FleetGeoResolverTest {

    private final FleetGeoResolver resolver = new FleetGeoResolver(new ParkGeoTransformService(new ParkPilotProperties()));

    @Test
    void shouldPreferExplicitGcj02FromDevice() {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setX(BigDecimal.valueOf(100));
        request.setY(BigDecimal.valueOf(200));
        request.setLongitude(new BigDecimal("121.070000"));
        request.setLatitude(new BigDecimal("31.920000"));

        var geo = resolver.resolve(request);

        assertTrue(geo.isPresent());
        assertEquals(new BigDecimal("121.070000"), geo.get().longitude());
        assertEquals(new BigDecimal("31.920000"), geo.get().latitude());
    }

    @Test
    void shouldDeriveGcj02FromParkXYWhenGpsMissing() {
        var geo = resolver.resolve(BigDecimal.valueOf(600), BigDecimal.valueOf(400), null, null);

        assertTrue(geo.isPresent());
        assertEquals(new BigDecimal("121.080354"), geo.get().longitude());
        assertEquals(new BigDecimal("31.961977"), geo.get().latitude());
    }
}
