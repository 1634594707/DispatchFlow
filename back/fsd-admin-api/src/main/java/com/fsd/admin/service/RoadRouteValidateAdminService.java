package com.fsd.admin.service;

import com.fsd.admin.vo.RoadRouteValidateRequest;
import com.fsd.admin.vo.RoadRouteValidateResponse;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RoadRouteValidation;
import com.fsd.dispatch.geo.RouteMetrics;
import com.fsd.dispatch.geo.RouteMetricsCalculator;
import com.fsd.dispatch.geo.RouteUnreachableReason;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoadRouteValidateAdminService {

    private final RoadRouteService roadRouteService;
    private final RoadRouteCollisionValidator collisionValidator;
    private final RouteMetricsCalculator routeMetricsCalculator;

    public RoadRouteValidateAdminService(RoadRouteService roadRouteService,
                                         RoadRouteCollisionValidator collisionValidator,
                                         RouteMetricsCalculator routeMetricsCalculator) {
        this.roadRouteService = roadRouteService;
        this.collisionValidator = collisionValidator;
        this.routeMetricsCalculator = routeMetricsCalculator;
    }

    public RoadRouteValidateResponse validate(RoadRouteValidateRequest request) {
        List<GeoPoint> polyline = resolvePolyline(request);
        RoadRouteValidation validation = collisionValidator.validate(polyline);
        String source = null;
        RouteUnreachableReason unreachableReason = null;
        String unreachableDetail = null;

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

                // P1-4: derive granular unreachable reason from the planned result
                if (planned.invalid()) {
                    unreachableReason = inferUnreachableReason(planned);
                    unreachableDetail = buildUnreachableDetail(planned, unreachableReason);
                }
            } else if (origin == null && destination == null) {
                unreachableReason = RouteUnreachableReason.START_OFF_ROAD;
                unreachableDetail = "Origin coordinates are required";
            } else if (origin == null) {
                unreachableReason = RouteUnreachableReason.START_OFF_ROAD;
                unreachableDetail = "Origin coordinates are required";
            } else {
                unreachableReason = RouteUnreachableReason.END_OFF_ROAD;
                unreachableDetail = "Destination coordinates are required";
            }
        }

        // P1-5: compute route metrics (length, ETA, risk points)
        RouteMetrics metrics = routeMetricsCalculator.compute(
                null, polyline, List.of(), null, null, null);

        return RoadRouteValidateResponse.builder()
                .invalid(validation.invalid())
                .crossesBuilding(validation.crossesBuilding())
                .crossesRiver(validation.crossesRiver())
                .nearestRoadDistanceMeters(validation.nearestRoadDistanceMeters())
                .vertexCount(polyline.size())
                .source(source)
                .unreachableReason(unreachableReason == null ? null : unreachableReason.code())
                .unreachableDetail(unreachableDetail)
                .totalLengthMeters(metrics.totalLengthMeters())
                .estimatedTravelSeconds(metrics.estimatedTravelSeconds())
                .waitingSeconds(metrics.waitingSeconds())
                .chargingSeconds(metrics.chargingSeconds())
                .riskPoints(metrics.riskPoints())
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

    private static RouteUnreachableReason inferUnreachableReason(RoadRouteResult result) {
        if (result.crossesBuilding()) {
            return RouteUnreachableReason.CROSSES_BUILDING;
        }
        if (result.crossesRiver()) {
            return RouteUnreachableReason.CROSSES_RIVER;
        }
        if (result.polyline() == null || result.polyline().isEmpty()) {
            return RouteUnreachableReason.NO_PATH_ON_GRAPH;
        }
        if (result.polyline().size() < 4) {
            return RouteUnreachableReason.NO_PATH_ON_GRAPH;
        }
        return null;
    }

    private static String buildUnreachableDetail(RoadRouteResult result, RouteUnreachableReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case CROSSES_BUILDING -> "Route polyline crosses a building polygon — service position may not be configured";
            case CROSSES_RIVER -> "Route polyline crosses a river / forbidden service yard";
            case NO_PATH_ON_GRAPH -> "No path found on the road graph between origin and destination";
            default -> reason.code();
        };
    }

    private static GeoPoint toPoint(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return new GeoPoint(lng, lat);
    }
}
