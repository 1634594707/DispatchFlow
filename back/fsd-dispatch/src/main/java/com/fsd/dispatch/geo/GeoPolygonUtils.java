package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
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

    public static boolean segmentIntersectsPolygon(GeoPoint a, GeoPoint b, List<GeoPoint> polygon) {
        if (polygon == null || polygon.size() < 3 || a == null || b == null) {
            return false;
        }
        if (pointInPolygon(a, polygon) || pointInPolygon(b, polygon)) {
            return true;
        }
        for (int i = 0; i < polygon.size(); i++) {
            GeoPoint c = polygon.get(i);
            GeoPoint d = polygon.get((i + 1) % polygon.size());
            if (segmentsIntersect(a, b, c, d)) {
                return true;
            }
        }
        return false;
    }

    public static boolean pointInPolygon(GeoPoint point, List<GeoPoint> polygon) {
        if (point == null || polygon == null || polygon.size() < 3) {
            return false;
        }
        double x = point.longitude().doubleValue();
        double y = point.latitude().doubleValue();
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i).longitude().doubleValue();
            double yi = polygon.get(i).latitude().doubleValue();
            double xj = polygon.get(j).longitude().doubleValue();
            double yj = polygon.get(j).latitude().doubleValue();
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 0D) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static boolean segmentsIntersect(GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        double ax = a.longitude().doubleValue();
        double ay = a.latitude().doubleValue();
        double bx = b.longitude().doubleValue();
        double by = b.latitude().doubleValue();
        double cx = c.longitude().doubleValue();
        double cy = c.latitude().doubleValue();
        double dx = d.longitude().doubleValue();
        double dy = d.latitude().doubleValue();
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        double o4 = orientation(cx, cy, dx, dy, bx, by);
        if (!sameOrientation(o1, o2) && !sameOrientation(o3, o4)) {
            return true;
        }
        if (isCollinear(o1) && onSegment(ax, ay, bx, by, cx, cy)) {
            return true;
        }
        if (isCollinear(o2) && onSegment(ax, ay, bx, by, dx, dy)) {
            return true;
        }
        if (isCollinear(o3) && onSegment(cx, cy, dx, dy, ax, ay)) {
            return true;
        }
        return isCollinear(o4) && onSegment(cx, cy, dx, dy, bx, by);
    }

    private static boolean sameOrientation(double left, double right) {
        return Math.abs(left - right) < 1e-12;
    }

    private static boolean isCollinear(double value) {
        return Math.abs(value) < 1e-12;
    }

    public static double distancePointToSegmentMeters(GeoPoint point, GeoPoint segStart, GeoPoint segEnd) {
        if (point == null || segStart == null || segEnd == null) {
            return Double.MAX_VALUE;
        }
        double px = point.longitude().doubleValue();
        double py = point.latitude().doubleValue();
        double ax = segStart.longitude().doubleValue();
        double ay = segStart.latitude().doubleValue();
        double bx = segEnd.longitude().doubleValue();
        double by = segEnd.latitude().doubleValue();
        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0D && dy == 0D) {
            return haversineMeters(point, segStart);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0D, Math.min(1D, t));
        GeoPoint projection = new GeoPoint(
                BigDecimal.valueOf(ax + t * dx),
                BigDecimal.valueOf(ay + t * dy));
        return haversineMeters(point, projection);
    }

    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000D * Math.asin(Math.sqrt(h));
    }

    private static double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
        double value = (by - ay) * (cx - bx) - (bx - ax) * (cy - by);
        if (Math.abs(value) < 1e-12) {
            return 0;
        }
        return value > 0 ? 1 : 2;
    }

    private static boolean onSegment(double ax, double ay, double bx, double by, double cx, double cy) {
        return cx <= Math.max(ax, bx) + 1e-12
                && cx + 1e-12 >= Math.min(ax, bx)
                && cy <= Math.max(ay, by) + 1e-12
                && cy + 1e-12 >= Math.min(ay, by);
    }
}
