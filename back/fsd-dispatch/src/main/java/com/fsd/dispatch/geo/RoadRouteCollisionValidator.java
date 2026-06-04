package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import com.fsd.dispatch.geo.local.OsmPilotGeoRepository;
import com.fsd.dispatch.geo.local.PilotForbiddenZones;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class RoadRouteCollisionValidator {

    private static final double OFF_ROAD_THRESHOLD_METERS = 45D;

    private final LocalPilotRoadGraphService localPilotRoadGraphService;
    private final OsmPilotGeoRepository osmPilotGeoRepository;

    public RoadRouteCollisionValidator(LocalPilotRoadGraphService localPilotRoadGraphService,
                                       OsmPilotGeoRepository osmPilotGeoRepository) {
        this.localPilotRoadGraphService = localPilotRoadGraphService;
        this.osmPilotGeoRepository = osmPilotGeoRepository;
    }

    public RoadRouteValidation validate(List<GeoPoint> polyline) {
        if (polyline == null || polyline.isEmpty()) {
            return RoadRouteValidation.valid(0D);
        }
        if (polyline.size() < 4) {
            double nearest = maxNearestRoadDistance(polyline);
            return RoadRouteValidation.invalid(false, false, nearest);
        }

        boolean crossesBuilding = crossesBuilding(polyline);
        boolean crossesRiver = crossesRiver(polyline);
        double nearest = maxNearestRoadDistance(polyline);
        boolean offRoad = nearest > OFF_ROAD_THRESHOLD_METERS;
        if (crossesBuilding || crossesRiver || offRoad) {
            return RoadRouteValidation.invalid(crossesBuilding, crossesRiver, nearest);
        }
        return RoadRouteValidation.valid(nearest);
    }

    public RoadRouteResult applyValidation(RoadRouteResult result) {
        if (result == null) {
            return result;
        }
        RoadRouteValidation validation = validate(result.polyline());
        if (result.source() == RoadRouteSource.STRAIGHT_LINE) {
            validation = RoadRouteValidation.invalid(
                    validation.crossesBuilding(),
                    validation.crossesRiver(),
                    validation.nearestRoadDistanceMeters());
        }
        return result.withValidation(validation);
    }

    private boolean crossesBuilding(List<GeoPoint> polyline) {
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            for (List<GeoPoint> block : buildingBlocks()) {
                if (block.size() < 3) {
                    continue;
                }
                if (GeoPolygonUtils.segmentIntersectsPolygon(a, b, block)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<List<GeoPoint>> buildingBlocks() {
        if (osmPilotGeoRepository.isLoaded()) {
            return osmPilotGeoRepository.buildings();
        }
        return PilotForbiddenZones.BUILDING_BLOCKS;
    }

    private boolean crossesRiver(List<GeoPoint> polyline) {
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            for (List<GeoPoint> river : PilotForbiddenZones.RIVER_ZONES) {
                if (GeoPolygonUtils.segmentIntersectsPolygon(a, b, river)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double maxNearestRoadDistance(List<GeoPoint> polyline) {
        List<List<GeoPoint>> roadSegments = localPilotRoadGraphService.allRoadSegments();
        double max = 0D;
        for (GeoPoint point : samplePoints(polyline)) {
            double nearest = nearestRoadDistanceMeters(point, roadSegments);
            if (nearest > max) {
                max = nearest;
            }
        }
        return max;
    }

    private static List<GeoPoint> samplePoints(List<GeoPoint> polyline) {
        List<GeoPoint> samples = new ArrayList<>(polyline);
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            samples.add(midpoint(a, b));
        }
        return samples;
    }

    private static GeoPoint midpoint(GeoPoint a, GeoPoint b) {
        double lng = (a.longitude().doubleValue() + b.longitude().doubleValue()) / 2D;
        double lat = (a.latitude().doubleValue() + b.latitude().doubleValue()) / 2D;
        return PilotForbiddenZones.g(lng, lat);
    }

    private static double nearestRoadDistanceMeters(GeoPoint point, List<List<GeoPoint>> roadSegments) {
        double min = Double.MAX_VALUE;
        for (List<GeoPoint> segment : roadSegments) {
            for (int i = 1; i < segment.size(); i++) {
                double dist = GeoPolygonUtils.distancePointToSegmentMeters(
                        point, segment.get(i - 1), segment.get(i));
                if (dist < min) {
                    min = dist;
                }
            }
        }
        return min == Double.MAX_VALUE ? 0D : min;
    }
}
