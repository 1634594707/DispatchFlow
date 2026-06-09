package com.fsd.dispatch.geo.local;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteFollower;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RoadRouteSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@Qualifier("localGraph")
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class LocalPilotRoadGraphService implements RoadRouteService {

    private static final double SNAP_THRESHOLD_METERS = 50D;

    private final Map<String, List<GeoPoint>> segmentCache = new LinkedHashMap<>();
    private final Map<String, GeoPoint> stationAnchors = new LinkedHashMap<>();
    private final OsmPilotGeoRepository osmPilotGeoRepository;

    public LocalPilotRoadGraphService(OsmPilotGeoRepository osmPilotGeoRepository) {
        this.osmPilotGeoRepository = osmPilotGeoRepository;
        initAnchors();
        // 手工走廊始终保留：OSM 子图不连通时作为回退，避免直线穿楼
        initSegments();
    }

    private void initAnchors() {
        stationAnchors.put("PICK01", g(121.074453, 31.960396));
        stationAnchors.put("PICK02", g(121.072682, 31.960646));
        stationAnchors.put("DROP01", g(121.079762, 31.963627));
        stationAnchors.put("DROP02", g(121.088022, 31.961825));
        stationAnchors.put("EXPRESS01", g(121.073500, 31.960550));
        stationAnchors.put("IDLE01", g(121.080354, 31.961977));
        stationAnchors.put("DROP03", g(121.074367, 31.963548));
        stationAnchors.put("DROP04", g(121.084000, 31.961977));
        stationAnchors.put("CHG04", g(121.075160, 31.960700));
        stationAnchors.put("CHG05", g(121.084500, 31.961900));
        stationAnchors.put("JUNCTION_SW", g(121.072682, 31.960646));
        stationAnchors.put("JUNCTION_SE", g(121.075160, 31.960646));
        stationAnchors.put("JUNCTION_NW", g(121.072682, 31.963523));
        stationAnchors.put("JUNCTION_NE", g(121.079152, 31.963523));
    }

    private void initSegments() {
        put("PICK01_DROP01", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.961000),
                g(121.075160, 31.961500),
                g(121.075160, 31.961977),
                g(121.076500, 31.961977),
                g(121.078000, 31.961977),
                g(121.079152, 31.961977),
                g(121.079152, 31.962500),
                g(121.079152, 31.963000),
                g(121.079152, 31.963523)));
        put("PICK01_EXPRESS01", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.960550),
                g(121.074500, 31.960550),
                g(121.073500, 31.960550)));
        put("PICK01_DROP02", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.961000),
                g(121.075160, 31.961500),
                g(121.075160, 31.961977),
                g(121.080354, 31.961977),
                g(121.084000, 31.961977),
                g(121.088022, 31.961825)));
        put("PICK02_DROP02", List.of(
                g(121.072682, 31.960646),
                g(121.075160, 31.960646),
                g(121.075160, 31.961000),
                g(121.075160, 31.961500),
                g(121.075160, 31.961977),
                g(121.080354, 31.961977),
                g(121.084000, 31.961977),
                g(121.088022, 31.961825)));
        put("PICK02_DROP01", List.of(
                g(121.072682, 31.960646),
                g(121.072682, 31.961500),
                g(121.072682, 31.961977),
                g(121.075160, 31.961977),
                g(121.078000, 31.961977),
                g(121.079152, 31.961977),
                g(121.079152, 31.962500),
                g(121.079152, 31.963523)));
        put("IDLE01_PICK01", List.of(
                g(121.080354, 31.961977),
                g(121.078000, 31.961977),
                g(121.075160, 31.961977),
                g(121.075160, 31.961500),
                g(121.075160, 31.961000),
                g(121.075160, 31.960424)));
        put("IDLE01_PICK02", List.of(
                g(121.080354, 31.961977),
                g(121.078000, 31.961977),
                g(121.075160, 31.961977),
                g(121.075160, 31.961000),
                g(121.075160, 31.960646),
                g(121.072682, 31.960646)));
        put("IDLE01_DROP01", List.of(
                g(121.080354, 31.961977),
                g(121.079800, 31.961977),
                g(121.079152, 31.961977),
                g(121.079152, 31.962500),
                g(121.079152, 31.963000),
                g(121.079152, 31.963523)));
        put("IDLE01_DROP02", List.of(
                g(121.080354, 31.961977),
                g(121.084000, 31.961977),
                g(121.088022, 31.961825)));
        put("EXPRESS01_DROP01", List.of(
                g(121.073500, 31.960550),
                g(121.073500, 31.961000),
                g(121.073500, 31.961500),
                g(121.073500, 31.961977),
                g(121.076000, 31.961977),
                g(121.078000, 31.961977),
                g(121.079152, 31.961977),
                g(121.079152, 31.962500),
                g(121.079152, 31.963523)));
        put("EXPRESS01_DROP02", List.of(
                g(121.073500, 31.960550),
                g(121.073500, 31.961000),
                g(121.073500, 31.961500),
                g(121.073500, 31.961977),
                g(121.080354, 31.961977),
                g(121.084000, 31.961977),
                g(121.088022, 31.961825)));
        put("EXPRESS01_PICK01", List.of(
                g(121.073500, 31.960550),
                g(121.074500, 31.960550),
                g(121.075160, 31.960550),
                g(121.075160, 31.960424)));
        put("DEFAULT_PILOT", List.of(
                g(121.072682, 31.960646),
                g(121.075160, 31.960646),
                g(121.075160, 31.961977),
                g(121.079152, 31.961977),
                g(121.079152, 31.963523),
                g(121.088022, 31.961825),
                g(121.088022, 31.961500),
                g(121.080354, 31.961977)));
        put("PICK01_DROP03", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.961000),
                g(121.075160, 31.961500),
                g(121.075160, 31.961977),
                g(121.073500, 31.961977),
                g(121.072682, 31.961977),
                g(121.072682, 31.962500),
                g(121.072682, 31.963000),
                g(121.073200, 31.963523)));
        put("PICK02_DROP03", List.of(
                g(121.072682, 31.960646),
                g(121.072682, 31.961500),
                g(121.072682, 31.961977),
                g(121.072682, 31.962500),
                g(121.072682, 31.963000),
                g(121.073200, 31.963523)));
        put("DROP03_DROP01", List.of(
                g(121.073200, 31.963523),
                g(121.075000, 31.963523),
                g(121.077000, 31.963523),
                g(121.079152, 31.963523)));
        put("DROP03_IDLE01", List.of(
                g(121.073200, 31.963523),
                g(121.072682, 31.963523),
                g(121.072682, 31.961977),
                g(121.075160, 31.961977),
                g(121.078000, 31.961977),
                g(121.080354, 31.961977)));
        put("PICK01_DROP04", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.961000),
                g(121.075160, 31.961500),
                g(121.075160, 31.961977),
                g(121.080354, 31.961977),
                g(121.084000, 31.961977)));
        put("IDLE01_DROP04", List.of(
                g(121.080354, 31.961977),
                g(121.084000, 31.961977)));
        put("DROP04_DROP02", List.of(
                g(121.084000, 31.961977),
                g(121.088022, 31.961825)));
        put("DROP02_DROP01", List.of(
                g(121.088022, 31.961825),
                g(121.084000, 31.961977),
                g(121.080354, 31.961977),
                g(121.079762, 31.963627)));
        put("PICK01_CHG04", List.of(
                g(121.075160, 31.960424),
                g(121.075160, 31.960700)));
        put("IDLE01_CHG04", List.of(
                g(121.080354, 31.961977),
                g(121.078000, 31.961977),
                g(121.075160, 31.961977),
                g(121.075160, 31.960700)));
        put("IDLE01_CHG05", List.of(
                g(121.080354, 31.961977),
                g(121.084000, 31.961977),
                g(121.084500, 31.961900)));
        put("DROP02_CHG05", List.of(
                g(121.088022, 31.961825),
                g(121.084500, 31.961900)));
    }

    private void put(String key, List<GeoPoint> polyline) {
        segmentCache.put(key, polyline);
        segmentCache.put(reverseKey(key), reverse(polyline));
    }

    private static String reverseKey(String key) {
        String[] parts = key.split("_");
        if (parts.length != 2) return key;
        return parts[1] + "_" + parts[0];
    }

    private static List<GeoPoint> reverse(List<GeoPoint> points) {
        List<GeoPoint> reversed = new ArrayList<>(points);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public RoadRouteResult planDrivingRoute(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE);
        }
        if (osmPilotGeoRepository.isLoaded()) {
            List<GeoPoint> osmPath = osmPilotGeoRepository.shortestRoadPath(origin, destination);
            if (osmPath.size() >= 2) {
                osmPath = RoadRouteFollower.fromPolyline(osmPath).polyline();
            }
            if (osmPath.size() >= 4) {
                return new RoadRouteResult(osmPath, haversinePath(osmPath), RoadRouteSource.LOCAL_GRAPH);
            }
            // OSM 子图不连通时回退手工走廊，避免 2 点直线穿楼
        }
        String key = nearestStationKey(origin) + "_" + nearestStationKey(destination);
        List<GeoPoint> polyline = segmentCache.get(key);
        if (polyline != null && polyline.size() >= 4) {
            return corridorRoute(refinePolylineAlongRoads(polyline));
        }
        List<GeoPoint> bestPolyline = findClosestSegment(origin, destination);
        if (bestPolyline != null && bestPolyline.size() >= 4) {
            return corridorRoute(refinePolylineAlongRoads(bestPolyline));
        }
        return new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE);
    }

    private RoadRouteResult corridorRoute(List<GeoPoint> polyline) {
        if (polyline == null || polyline.size() < 4) {
            return new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE);
        }
        return new RoadRouteResult(polyline, haversinePath(polyline), RoadRouteSource.LOCAL_GRAPH);
    }

    /** 将手工走廊折线按 OSM 路网分段最短路径重连，避免穿建筑。 */
    private List<GeoPoint> refinePolylineAlongRoads(List<GeoPoint> polyline) {
        if (!osmPilotGeoRepository.isLoaded() || polyline == null || polyline.size() < 2) {
            return polyline;
        }
        List<GeoPoint> refined = new ArrayList<>();
        GeoPoint cursor = polyline.get(0);
        OsmPilotGeoRepository.OptionalSnap startSnap = osmPilotGeoRepository.snapToRoad(cursor);
        refined.add(startSnap.point() != null ? startSnap.point() : cursor);
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint waypoint = polyline.get(i);
            List<GeoPoint> leg = osmPilotGeoRepository.shortestRoadPath(refined.get(refined.size() - 1), waypoint);
            if (leg.size() >= 2) {
                leg = RoadRouteFollower.fromPolyline(leg).polyline();
                for (int j = 1; j < leg.size(); j++) {
                    refined.add(leg.get(j));
                }
            } else {
                OsmPilotGeoRepository.OptionalSnap snap = osmPilotGeoRepository.snapToRoad(waypoint);
                refined.add(snap.point() != null ? snap.point() : waypoint);
            }
        }
        List<GeoPoint> densified = RoadRouteFollower.fromPolyline(refined).polyline();
        return densified.size() >= 4 ? densified : polyline;
    }

    private String nearestStationKey(GeoPoint point) {
        double best = Double.MAX_VALUE;
        String bestKey = "IDLE01";
        for (var entry : stationAnchors.entrySet()) {
            double dist = haversineMeters(point, entry.getValue());
            if (dist < best) {
                best = dist;
                bestKey = entry.getKey();
            }
        }
        return bestKey;
    }

    private List<GeoPoint> findClosestSegment(GeoPoint origin, GeoPoint destination) {
        List<GeoPoint> bestPolyline = null;
        double bestDist = Double.MAX_VALUE;
        for (List<GeoPoint> polyline : segmentCache.values()) {
            if (polyline.size() < 4) continue;
            double originDist = nearestPointDistance(origin, polyline);
            double destDist = nearestPointDistance(destination, polyline);
            double total = originDist + destDist;
            if (total < bestDist) {
                bestDist = total;
                bestPolyline = polyline;
            }
        }
        return bestPolyline;
    }

    public Optional<GeoPoint> snapToRoad(GeoPoint point) {
        if (point == null) return Optional.empty();
        if (osmPilotGeoRepository.isLoaded()) {
            OsmPilotGeoRepository.OptionalSnap snap = osmPilotGeoRepository.snapToRoad(point);
            if (!snap.isEmpty()) {
                return Optional.of(snap.point());
            }
            return Optional.empty();
        }
        GeoPoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (GeoPoint vertex : allRoadVertices()) {
            double dist = haversineMeters(point, vertex);
            if (dist < bestDist) {
                bestDist = dist;
                best = vertex;
            }
        }
        if (best != null && bestDist <= SNAP_THRESHOLD_METERS) {
            return Optional.of(best);
        }
        return Optional.empty();
    }

    public List<GeoPoint> allRoadVertices() {
        if (osmPilotGeoRepository.isLoaded()) {
            return osmPilotGeoRepository.roadVertices();
        }
        java.util.Set<GeoPoint> vertices = new java.util.LinkedHashSet<>(stationAnchors.values());
        for (List<GeoPoint> seg : segmentCache.values()) {
            vertices.addAll(seg);
        }
        return new ArrayList<>(vertices);
    }

    public List<List<GeoPoint>> allRoadSegments() {
        if (osmPilotGeoRepository.isLoaded()) {
            return osmPilotGeoRepository.roadSegments();
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<List<GeoPoint>> segments = new ArrayList<>();
        for (Map.Entry<String, List<GeoPoint>> entry : segmentCache.entrySet()) {
            String key = entry.getKey();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            seen.add(reverseKey(key));
            segments.add(entry.getValue());
        }
        return segments;
    }

    public int segmentCount() {
        return segmentCache.size() / 2;
    }

    public Map<String, GeoPoint> getStationAnchors() {
        return java.util.Collections.unmodifiableMap(stationAnchors);
    }

    private static double nearestPointDistance(GeoPoint point, List<GeoPoint> polyline) {
        double min = Double.MAX_VALUE;
        for (GeoPoint vertex : polyline) {
            double dist = haversineMeters(point, vertex);
            if (dist < min) min = dist;
        }
        return min;
    }

    static double haversinePath(List<GeoPoint> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }

    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000D * Math.asin(Math.sqrt(h));
    }

    static GeoPoint g(double lng, double lat) {
        return new GeoPoint(BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP));
    }
}
