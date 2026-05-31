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
        assertEquals(new BigDecimal("121.062280"), geo.get().longitude());
        assertEquals(new BigDecimal("31.912450"), geo.get().latitude());
    }
}
