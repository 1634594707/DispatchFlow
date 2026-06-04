package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadRouteFollowerTest {

    @Test
    void advancesAlongPolylineAndReachesEnd() {
        GeoPoint a = new GeoPoint(new BigDecimal("121.060000"), new BigDecimal("31.910000"));
        GeoPoint b = new GeoPoint(new BigDecimal("121.065000"), new BigDecimal("31.915000"));
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(List.of(a, b));
        assertTrue(follower.totalMeters() > 0D);

        for (int i = 0; i < 200 && !follower.isComplete(); i++) {
            follower.advanceMeters(50D);
        }
        assertTrue(follower.isComplete());
        assertEquals(b.longitude(), follower.currentPosition().longitude());
        assertEquals(b.latitude(), follower.currentPosition().latitude());
    }

    @Test
    void distanceMonotonicOnMultiVertexPolyline() {
        List<GeoPoint> polyline = List.of(
                g(121.060000, 31.910000),
                g(121.061000, 31.911000),
                g(121.062000, 31.912000),
                g(121.063000, 31.913000),
                g(121.064000, 31.914000));
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(polyline);
        double total = follower.totalMeters();
        assertTrue(total > 0D, "多顶点折线应有正距离");

        double prevTraveled = 0D;
        for (int i = 0; i < 100; i++) {
            follower.advanceMeters(total / 100D);
            double traveled = follower.traveledMeters();
            assertTrue(traveled >= prevTraveled, "traveledMeters 应单调递增: step " + i);
            prevTraveled = traveled;
        }
        assertTrue(follower.isComplete(), "应到达终点");
    }

    @Test
    void headingContinuousOnMultiVertexPolyline() {
        List<GeoPoint> polyline = List.of(
                g(121.060000, 31.910000),
                g(121.061000, 31.911000),
                g(121.062000, 31.912000),
                g(121.063000, 31.913000),
                g(121.064000, 31.914000));
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(polyline);
        double total = follower.totalMeters();

        double prevHeading = follower.headingDegrees();
        int angleJumps = 0;
        for (int i = 0; i < 200; i++) {
            follower.advanceMeters(total / 200D);
            double heading = follower.headingDegrees();
            double diff = Math.abs(heading - prevHeading);
            if (diff > 180D) diff = 360D - diff;
            if (diff > 90D) angleJumps++;
            prevHeading = heading;
        }
        assertTrue(angleJumps <= 2, "heading 应连续变化，大角度跳变应 ≤2 次，实际: " + angleJumps);
    }

    @Test
    void emptyPolylineHandled() {
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(List.of());
        assertTrue(follower.isEmpty());
        assertEquals(0D, follower.totalMeters());
        assertEquals(1D, follower.progressRatio());
    }

    @Test
    void singlePointPolylineHandled() {
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(List.of(g(121.060000, 31.910000)));
        assertTrue(follower.isEmpty());
        assertEquals(0D, follower.totalMeters());
    }

    @Test
    void resetReturnsToStart() {
        GeoPoint a = g(121.060000, 31.910000);
        GeoPoint b = g(121.065000, 31.915000);
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(List.of(a, b));
        follower.advanceMeters(100D);
        assertTrue(follower.traveledMeters() > 0D);
        follower.reset();
        assertEquals(0D, follower.traveledMeters());
        assertEquals(a.longitude(), follower.currentPosition().longitude());
    }

    @Test
    void polylineReturnsDensifiedPoints() {
        List<GeoPoint> input = List.of(
                g(121.060000, 31.910000),
                g(121.062000, 31.912000),
                g(121.064000, 31.914000));
        RoadRouteFollower follower = RoadRouteFollower.fromPolyline(input);
        List<GeoPoint> densified = follower.polyline();
        assertTrue(densified.size() >= input.size(), "densified polyline 应 ≥ 原始顶点数");
        assertEquals(input.get(0), densified.get(0), "起点一致");
        assertEquals(input.get(input.size() - 1), densified.get(densified.size() - 1), "终点一致");
    }

    private static GeoPoint g(double lng, double lat) {
        return new GeoPoint(BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP));
    }
}