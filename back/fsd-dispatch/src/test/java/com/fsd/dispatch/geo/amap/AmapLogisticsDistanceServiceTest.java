package com.fsd.dispatch.geo.amap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.AmapProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AmapLogisticsDistanceServiceTest {

    @Test
    void shouldReportUnavailableWithoutKey() {
        AmapProperties properties = new AmapProperties();
        properties.getLogistics().setEnabled(true);
        AmapLogisticsDistanceService disabled = new AmapLogisticsDistanceService(
                properties, new ParkGeoTransformService(new ParkPilotProperties()), new ObjectMapper());
        assertFalse(disabled.isAvailable());
    }

    @Test
    void shouldBlendParkAndGeoDistances() {
        List<GeoPoint> origins = List.of(new GeoPoint(BigDecimal.valueOf(121.06), BigDecimal.valueOf(31.91)));
        GeoPoint dest = new GeoPoint(BigDecimal.valueOf(121.07), BigDecimal.valueOf(31.92));
        List<Double> parkPx = List.of(100D);
        AmapLogisticsDistanceService blendOnly = new AmapLogisticsDistanceService(
                new AmapProperties(), new ParkGeoTransformService(new ParkPilotProperties()), new ObjectMapper()) {
            @Override
            public Optional<List<Double>> fetchDrivingDistancesMeters(List<GeoPoint> o, GeoPoint d) {
                return Optional.of(List.of(2000D));
            }
        };
        Map<Integer, Double> blended = blendOnly.blendDistances(origins, dest, parkPx, 0.5);
        assertEquals(550D, blended.get(0), 0.01);
    }

    @Test
    void shouldConvertMetersToParkPx() {
        AmapProperties properties = new AmapProperties();
        properties.setWebServiceKey("test-key");
        properties.getLogistics().setEnabled(true);
        AmapLogisticsDistanceService service = new AmapLogisticsDistanceService(
                properties, new ParkGeoTransformService(new ParkPilotProperties()), new ObjectMapper());
        assertEquals(500D, service.metersToParkPx(1000D), 0.01);
        assertEquals(Double.MAX_VALUE, service.metersToParkPx(Double.POSITIVE_INFINITY));
    }
}
