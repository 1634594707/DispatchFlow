package com.fsd.dispatch.geo;

public record RoadRouteValidation(
        boolean invalid,
        boolean crossesBuilding,
        boolean crossesRiver,
        double nearestRoadDistanceMeters) {

    public static RoadRouteValidation valid(double nearestRoadDistanceMeters) {
        return new RoadRouteValidation(false, false, false, nearestRoadDistanceMeters);
    }

    public static RoadRouteValidation invalid(
            boolean crossesBuilding, boolean crossesRiver, double nearestRoadDistanceMeters) {
        return new RoadRouteValidation(true, crossesBuilding, crossesRiver, nearestRoadDistanceMeters);
    }
}
