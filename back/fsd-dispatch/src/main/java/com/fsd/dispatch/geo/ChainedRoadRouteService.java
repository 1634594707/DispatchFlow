package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class ChainedRoadRouteService implements RoadRouteService {

    private static final Logger log = LoggerFactory.getLogger(ChainedRoadRouteService.class);

    private final RoadRouteService amapService;
    private final RoadRouteService localGraphService;
    private final RoadRouteCollisionValidator collisionValidator;

    public ChainedRoadRouteService(@Qualifier("amap") RoadRouteService amapService,
                                   @Qualifier("localGraph") RoadRouteService localGraphService,
                                   RoadRouteCollisionValidator collisionValidator) {
        this.amapService = amapService;
        this.localGraphService = localGraphService;
        this.collisionValidator = collisionValidator;
    }

    public RoadRouteService getAmapService() {
        return amapService;
    }

    public RoadRouteService getLocalGraphService() {
        return localGraphService;
    }

    @Override
    public boolean isAvailable() {
        return amapService.isAvailable() || localGraphService.isAvailable();
    }

    @Override
    public RoadRouteResult planDrivingRoute(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return straightLine(origin, destination);
        }
        if (amapService.isAvailable()) {
            RoadRouteResult result = collisionValidator.applyValidation(
                    amapService.planDrivingRoute(origin, destination));
            if (result.fromAmap() && result.polyline().size() >= 4 && !result.invalid()) {
                return result;
            }
            if (result.invalid()) {
                log.info("Amap route rejected by collision check (building={}, river={}), falling back to local graph",
                        result.crossesBuilding(), result.crossesRiver());
            } else {
                log.debug("Amap route returned {} vertices, falling back to local graph", result.polyline().size());
            }
        }
        if (localGraphService.isAvailable()) {
            RoadRouteResult result = collisionValidator.applyValidation(
                    localGraphService.planDrivingRoute(origin, destination));
            if (result.fromLocalGraph() && result.polyline().size() >= 4 && !result.invalid()) {
                return result;
            }
            log.debug("Local graph returned {} vertices (invalid={}), no straight-line fallback",
                    result.polyline().size(), result.invalid());
        }
        return collisionValidator.applyValidation(new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE));
    }

    private static RoadRouteResult straightLine(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE);
        }
        List<GeoPoint> line = List.of(origin, destination);
        double meters = RoadRouteFollower.fromPolyline(line).totalMeters();
        return new RoadRouteResult(line, meters, RoadRouteSource.STRAIGHT_LINE);
    }
}