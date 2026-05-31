package com.fsd.dispatch.geo;

import java.math.BigDecimal;
import java.util.List;

public final class GeoPolygonUtils {

    private GeoPolygonUtils() {
    }

    /**
     * Ray-casting algorithm for GCJ-02 polygon containment.
     *
     * @param polygon vertices as [lng, lat]
     */
    public static boolean contains(List<double[]> polygon, BigDecimal longitude, BigDecimal latitude) {
        if (polygon == null || polygon.size() < 3 || longitude == null || latitude == null) {
            return false;
        }
        double x = longitude.doubleValue();
        double y = latitude.doubleValue();
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i)[0];
            double yi = polygon.get(i)[1];
            double xj = polygon.get(j)[0];
            double yj = polygon.get(j)[1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 0.0d) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
