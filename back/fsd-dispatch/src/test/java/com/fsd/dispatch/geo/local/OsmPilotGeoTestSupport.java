package com.fsd.dispatch.geo.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import java.nio.file.Path;

public final class OsmPilotGeoTestSupport {

    private OsmPilotGeoTestSupport() {
    }

    public static LocalPilotRoadGraphService graph() {
        return new LocalPilotRoadGraphService(repository());
    }

    public static RoadRouteCollisionValidator validator() {
        return new RoadRouteCollisionValidator(graph(), repository());
    }

    public static StationCoordinateValidator stationValidator() {
        return new StationCoordinateValidator(graph(), repository());
    }

    public static OsmPilotGeoRepository repository() {
        ParkPilotProperties properties = new ParkPilotProperties();
        Path dataPath = Path.of("..", "..", "data", "pilot_osm_geo.json").normalize();
        properties.getGeo().setOsmGeoPath(dataPath.toString());
        return new OsmPilotGeoRepository(properties, new ObjectMapper());
    }
}
