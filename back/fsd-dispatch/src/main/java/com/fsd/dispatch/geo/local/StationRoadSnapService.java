package com.fsd.dispatch.geo.local;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StationRoadSnapService {

    private static final Logger log = LoggerFactory.getLogger(StationRoadSnapService.class);

    private final LocalPilotRoadGraphService localPilotRoadGraphService;

    public StationRoadSnapService(LocalPilotRoadGraphService localPilotRoadGraphService) {
        this.localPilotRoadGraphService = localPilotRoadGraphService;
    }

    public Optional<GeoPoint> snapToNearestRoad(GeoPoint point) {
        return localPilotRoadGraphService.snapToRoad(point);
    }

    public Optional<GeoPoint> snapToNearestRoad(double lng, double lat) {
        return snapToNearestRoad(LocalPilotRoadGraphService.g(lng, lat));
    }
}