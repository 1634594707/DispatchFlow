package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 沿道路 polyline 按弧长插值移动（GCJ-02）。 */
public final class RoadRouteFollower {

    private final List<GeoPoint> points;
    private final double[] cumulativeMeters;
    private final double totalMeters;
    private double traveledMeters;

    private RoadRouteFollower(List<GeoPoint> points, double[] cumulativeMeters, double totalMeters) {
        this.points = points;
        this.cumulativeMeters = cumulativeMeters;
        this.totalMeters = totalMeters;
    }

    public static RoadRouteFollower fromPolyline(List<GeoPoint> polyline) {
        if (polyline == null || polyline.size() < 2) {
            return new RoadRouteFollower(List.of(), new double[] {0D}, 0D);
        }
        List<GeoPoint> dense = densify(polyline, 8);
        double[] cumulative = new double[dense.size()];
        cumulative[0] = 0D;
        for (int i = 1; i < dense.size(); i++) {
            cumulative[i] = cumulative[i - 1] + haversineMeters(dense.get(i - 1), dense.get(i));
        }
        return new RoadRouteFollower(dense, cumulative, cumulative[cumulative.length - 1]);
    }

    public boolean isEmpty() {
        return points.size() < 2 || totalMeters <= 0D;
    }

    public List<GeoPoint> polyline() {
        return points;
    }

    public double totalMeters() {
        return totalMeters;
    }

    public double traveledMeters() {
        return traveledMeters;
    }

    public double progressRatio() {
        if (totalMeters <= 0D) {
            return 1D;
        }
        return Math.min(1D, traveledMeters / totalMeters);
    }

    public GeoPoint currentPosition() {
        if (points.isEmpty()) {
            return null;
        }
        if (points.size() == 1 || traveledMeters <= 0D) {
            return points.getFirst();
        }
        if (traveledMeters >= totalMeters) {
            return points.getLast();
        }
        for (int i = 1; i < cumulativeMeters.length; i++) {
            if (traveledMeters <= cumulativeMeters[i]) {
                double segStart = cumulativeMeters[i - 1];
                double segLen = cumulativeMeters[i] - segStart;
                double ratio = segLen <= 0D ? 1D : (traveledMeters - segStart) / segLen;
                return interpolate(points.get(i - 1), points.get(i), ratio);
            }
        }
        return points.getLast();
    }

    /** @return heading degrees (0=north, clockwise) for map marker rotation */
    public double headingDegrees() {
        if (points.size() < 2) {
            return 0D;
        }
        GeoPoint from = currentPosition();
        int idx = indexAt(traveledMeters);
        GeoPoint to = idx < points.size() - 1 ? points.get(idx + 1) : points.get(idx);
        if (from == null) {
            from = points.get(Math.max(0, idx));
        }
        double dx = to.longitude().doubleValue() - from.longitude().doubleValue();
        double dy = to.latitude().doubleValue() - from.latitude().doubleValue();
        if (Math.hypot(dx, dy) < 1e-9) {
            return 0D;
        }
        return (Math.toDegrees(Math.atan2(dx, dy)) + 360D) % 360D;
    }

    public void advanceMeters(double deltaMeters) {
        if (deltaMeters <= 0 || totalMeters <= 0D) {
            return;
        }
        traveledMeters = Math.min(totalMeters, traveledMeters + deltaMeters);
    }

    public boolean isComplete() {
        return totalMeters <= 0D || traveledMeters >= totalMeters - 0.5D;
    }

    public void reset() {
        traveledMeters = 0D;
    }

    private int indexAt(double meters) {
        for (int i = 1; i < cumulativeMeters.length; i++) {
            if (meters <= cumulativeMeters[i]) {
                return i - 1;
            }
        }
        return Math.max(0, cumulativeMeters.length - 2);
    }

    private static GeoPoint interpolate(GeoPoint a, GeoPoint b, double ratio) {
        double t = Math.max(0D, Math.min(1D, ratio));
        double lng = a.longitude().doubleValue() + (b.longitude().doubleValue() - a.longitude().doubleValue()) * t;
        double lat = a.latitude().doubleValue() + (b.latitude().doubleValue() - a.latitude().doubleValue()) * t;
        return new GeoPoint(scale(lng), scale(lat));
    }

    private static List<GeoPoint> densify(List<GeoPoint> source, int minPoints) {
        if (source.size() >= minPoints) {
            return new ArrayList<>(source);
        }
        List<GeoPoint> result = new ArrayList<>();
        for (int i = 0; i < source.size() - 1; i++) {
            GeoPoint a = source.get(i);
            GeoPoint b = source.get(i + 1);
            result.add(a);
            int steps = Math.max(1, minPoints / Math.max(1, source.size() - 1));
            for (int s = 1; s < steps; s++) {
                double t = s / (double) steps;
                result.add(interpolate(a, b, t));
            }
        }
        result.add(source.getLast());
        return result;
    }

    private static double haversineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000D * Math.asin(Math.sqrt(h));
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private RoadRouteFollower() {
        throw new UnsupportedOperationException();
    }
}
