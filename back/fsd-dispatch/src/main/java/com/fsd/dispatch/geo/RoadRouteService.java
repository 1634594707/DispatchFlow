package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;

public interface RoadRouteService {

    boolean isAvailable();

    RoadRouteResult planDrivingRoute(GeoPoint origin, GeoPoint destination);
}
