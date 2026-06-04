package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.util.List;

public record RoadRouteResult(
        List<GeoPoint> polyline,
        double distanceMeters,
        RoadRouteSource source,
        boolean invalid,
        boolean crossesBuilding,
        boolean crossesRiver,
        double nearestRoadDistanceMeters) {

    public RoadRouteResult(List<GeoPoint> polyline, double distanceMeters, RoadRouteSource source) {
        this(polyline, distanceMeters, source, false, false, false, 0D);
    }

    public RoadRouteResult {
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
        if (source == null) {
            source = RoadRouteSource.STRAIGHT_LINE;
        }
    }

    public boolean fromAmap() {
        return source == RoadRouteSource.AMAP;
    }

    public boolean fromLocalGraph() {
        return source == RoadRouteSource.LOCAL_GRAPH;
    }

    public RoadRouteResult withValidation(RoadRouteValidation validation) {
        if (validation == null) {
            return this;
        }
        return new RoadRouteResult(
                polyline,
                distanceMeters,
                source,
                validation.invalid(),
                validation.crossesBuilding(),
                validation.crossesRiver(),
                validation.nearestRoadDistanceMeters());
    }
}
