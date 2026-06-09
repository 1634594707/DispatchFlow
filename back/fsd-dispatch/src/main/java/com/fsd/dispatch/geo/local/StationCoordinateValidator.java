package com.fsd.dispatch.geo.local;

import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StationCoordinateValidator {

    private static final Logger log = LoggerFactory.getLogger(StationCoordinateValidator.class);

    /** 叠石桥 L1 试点围栏 bbox（GCJ-02 · 与 V27 / zjfPilotGeo.ts 一致） */
    private static final double ZJF_MIN_LNG = 121.072051;
    private static final double ZJF_MAX_LNG = 121.088674;
    private static final double ZJF_MIN_LAT = 31.959885;
    private static final double ZJF_MAX_LAT = 31.964101;

    /** V4-S2：站点须贴近 OSM 道路，过宽阈值会导致路径规划失败。 */
    public static final double ROAD_SNAP_DISTANCE_METERS = 30D;

    private final LocalPilotRoadGraphService localPilotRoadGraphService;
    private final OsmPilotGeoRepository osmPilotGeoRepository;

    public StationCoordinateValidator(LocalPilotRoadGraphService localPilotRoadGraphService,
                                      OsmPilotGeoRepository osmPilotGeoRepository) {
        this.localPilotRoadGraphService = localPilotRoadGraphService;
        this.osmPilotGeoRepository = osmPilotGeoRepository;
    }

    public ValidationResult validate(GeoPoint point) {
        if (point == null) {
            return new ValidationResult(false, List.of("站点坐标为 null"));
        }
        List<String> warnings = new ArrayList<>();
        boolean pass = true;
        if (!withinBounds(point)) {
            warnings.add("坐标超出 L1 试点围栏范围");
            pass = false;
        }
        if (inForbiddenZone(point)) {
            warnings.add("坐标落在禁行建筑块或服务场区内，车辆无法直达");
            pass = false;
        }
        double roadDist = nearestRoadDistance(point);
        if (roadDist > ROAD_SNAP_DISTANCE_METERS) {
            warnings.add(String.format(Locale.ROOT, "坐标距最近道路顶点约 %.0f 米（阈值 %.0f 米），可能不在道路上",
                    roadDist, ROAD_SNAP_DISTANCE_METERS));
            pass = false;
        }
        return new ValidationResult(pass, warnings);
    }

    public ValidationResult validate(double lng, double lat) {
        return validate(new GeoPoint(
                BigDecimal.valueOf(lng), BigDecimal.valueOf(lat)));
    }

    public boolean withinBounds(double lng, double lat) {
        return lng >= ZJF_MIN_LNG && lng <= ZJF_MAX_LNG
                && lat >= ZJF_MIN_LAT && lat <= ZJF_MAX_LAT;
    }

    private boolean withinBounds(GeoPoint point) {
        return withinBounds(point.longitude().doubleValue(), point.latitude().doubleValue());
    }

    private boolean inForbiddenZone(GeoPoint point) {
        if (osmPilotGeoRepository.isLoaded()) {
            for (List<GeoPoint> block : osmPilotGeoRepository.buildings()) {
                if (GeoPolygonUtils.pointInPolygon(point, block)) {
                    return true;
                }
            }
        } else {
            for (List<GeoPoint> block : PilotForbiddenZones.BUILDING_BLOCKS) {
                if (GeoPolygonUtils.pointInPolygon(point, block)) {
                    return true;
                }
            }
        }
        return GeoPolygonUtils.pointInPolygon(point, PilotForbiddenZones.SERVICE_YARD);
    }

    private double nearestRoadDistance(GeoPoint point) {
        if (osmPilotGeoRepository.isLoaded()) {
            return osmPilotGeoRepository.nearestRoadDistanceMeters(point);
        }
        double minDist = Double.MAX_VALUE;
        for (GeoPoint vertex : localPilotRoadGraphService.allRoadVertices()) {
            double dist = LocalPilotRoadGraphService.haversineMeters(point, vertex);
            if (dist < minDist) minDist = dist;
        }
        return minDist;
    }

    public record ValidationResult(boolean valid, List<String> warnings) {

        public String summary() {
            if (valid) return "通过";
            return "不通过: " + String.join("; ", warnings);
        }
    }
}