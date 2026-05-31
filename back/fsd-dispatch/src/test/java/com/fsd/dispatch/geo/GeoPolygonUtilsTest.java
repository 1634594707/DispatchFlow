package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoPolygonUtilsTest {

    private static final List<double[]> SQUARE = List.of(
            new double[] {121.05, 31.90},
            new double[] {121.07, 31.90},
            new double[] {121.07, 31.92},
            new double[] {121.05, 31.92}
    );

    @Test
    void containsShouldDetectInsidePoint() {
        assertTrue(GeoPolygonUtils.contains(SQUARE, new BigDecimal("121.060000"), new BigDecimal("31.910000")));
    }

    @Test
    void containsShouldRejectOutsidePoint() {
        assertFalse(GeoPolygonUtils.contains(SQUARE, new BigDecimal("121.080000"), new BigDecimal("31.910000")));
    }
}
