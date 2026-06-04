package com.fsd.admin.service;

import com.fsd.admin.vo.RoadRouteValidateRequest;
import com.fsd.admin.vo.RoadRouteValidateResponse;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RoadRouteValidation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoadRouteValidateAdminService {

    private final RoadRouteService roadRouteService;
    private final RoadRouteCollisionValidator collisionValidator;

    public RoadRouteValidateAdminService(RoadRouteService roadRouteService,
                                         RoadRouteCollisionValidator collisionValidator) {
        this.roadRouteService = roadRouteService;
        this.collisionValidator = collisionValidator;
    }

    public RoadRouteValidateResponse validate(RoadRouteValidateRequest request) {
        List<GeoPoint> polyline = resolvePolyline(request);
        RoadRouteValidation validation = collisionValidator.validate(polyline);
        String source = null;
        if (request.getPolyline() == null || request.getPolyline().isEmpty()) {
            GeoPoint origin = toPoint(request.getOriginLng(), request.getOriginLat());
            GeoPoint destination = toPoint(request.getDestinationLng(), request.getDestinationLat());
            if (origin != null && destination != null) {
                RoadRouteResult planned = roadRouteService.planDrivingRoute(origin, destination);
                polyline = planned.polyline();
                validation = new RoadRouteValidation(
                        planned.invalid(),
                        planned.crossesBuilding(),
                        planned.crossesRiver(),
                        planned.nearestRoadDistanceMeters());
                source = planned.source().name();
            }
        }
        return RoadRouteValidateResponse.builder()
                .invalid(validation.invalid())
                .crossesBuilding(validation.crossesBuilding())
                .crossesRiver(validation.crossesRiver())
                .nearestRoadDistanceMeters(validation.nearestRoadDistanceMeters())
                .vertexCount(polyline.size())
                .source(source)
                .build();
    }

    private List<GeoPoint> resolvePolyline(RoadRouteValidateRequest request) {
        if (request.getPolyline() != null && !request.getPolyline().isEmpty()) {
            List<GeoPoint> points = new ArrayList<>();
            for (RoadRouteValidateRequest.RoadRoutePointDto point : request.getPolyline()) {
                GeoPoint geo = toPoint(point.getLongitude(), point.getLatitude());
                if (geo != null) {
                    points.add(geo);
                }
            }
            return points;
        }
        return List.of();
    }

    private static GeoPoint toPoint(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return new GeoPoint(lng, lat);
    }
}
