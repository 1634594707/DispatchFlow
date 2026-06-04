package com.fsd.dispatch.geo.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OSM-derived pilot geometry from {@code data/pilot_osm_geo.json} (GCJ-02).
 * Used for building collision checks and road-network shortest-path routing.
 */
@Component
public class OsmPilotGeoRepository {

    private static final Logger log = LoggerFactory.getLogger(OsmPilotGeoRepository.class);
    private static final double SNAP_THRESHOLD_METERS = 120D;
    private static final double NODE_MERGE_METERS = 3D;

    private final List<List<GeoPoint>> buildings = new ArrayList<>();
    private final List<List<GeoPoint>> roadPolylines = new ArrayList<>();
    private final Map<String, GeoPoint> graphNodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacency = new HashMap<>();

    public OsmPilotGeoRepository(ParkPilotProperties parkPilotProperties, ObjectMapper objectMapper) {
        load(objectMapper, parkPilotProperties.getGeo().getOsmGeoPath());
        buildRoadGraph();
        log.info("OSM pilot geo loaded: {} buildings, {} road polylines, {} graph nodes",
                buildings.size(), roadPolylines.size(), graphNodes.size());
    }

    public boolean isLoaded() {
        return !roadPolylines.isEmpty();
    }

    public List<List<GeoPoint>> buildings() {
        return List.copyOf(buildings);
    }

    public List<List<GeoPoint>> roadSegments() {
        return List.copyOf(roadPolylines);
    }

    public List<GeoPoint> roadVertices() {
        return new ArrayList<>(graphNodes.values());
    }

    public OptionalSnap snapToRoad(GeoPoint point) {
        if (point == null || roadPolylines.isEmpty()) {
            return OptionalSnap.empty();
        }
        double bestDist = Double.MAX_VALUE;
        GeoPoint bestPoint = null;
        GeoPoint bestSegStart = null;
        GeoPoint bestSegEnd = null;
        for (List<GeoPoint> polyline : roadPolylines) {
            for (int i = 1; i < polyline.size(); i++) {
                GeoPoint a = polyline.get(i - 1);
                GeoPoint b = polyline.get(i);
                GeoPoint projected = projectOnSegment(point, a, b);
                double dist = GeoPolygonUtils.haversineMeters(point, projected);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPoint = projected;
                    bestSegStart = a;
                    bestSegEnd = b;
                }
            }
        }
        if (bestPoint != null && bestDist <= SNAP_THRESHOLD_METERS) {
            return new OptionalSnap(bestPoint, nodeKey(bestPoint), bestDist, bestSegStart, bestSegEnd);
        }
        return OptionalSnap.empty();
    }

    public List<GeoPoint> shortestRoadPath(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null || roadPolylines.isEmpty()) {
            return List.of();
        }
        OptionalSnap fromSnap = snapToRoad(origin);
        OptionalSnap toSnap = snapToRoad(destination);
        if (fromSnap.isEmpty() || toSnap.isEmpty()) {
            return List.of();
        }
        String fromKey = ensureNode(fromSnap.point());
        String toKey = ensureNode(toSnap.point());
        connectSnapToRoadSegment(fromKey, fromSnap.point(), fromSnap.segStart(), fromSnap.segEnd());
        connectSnapToRoadSegment(toKey, toSnap.point(), toSnap.segStart(), toSnap.segEnd());
        List<GeoPoint> networkPath = dijkstra(fromKey, toKey);
        if (networkPath.size() < 2) {
            return List.of();
        }
        List<GeoPoint> path = new ArrayList<>();
        path.add(origin);
        if (GeoPolygonUtils.haversineMeters(origin, fromSnap.point()) > 1D) {
            path.add(fromSnap.point());
        }
        for (int i = 1; i < networkPath.size() - 1; i++) {
            path.add(networkPath.get(i));
        }
        if (GeoPolygonUtils.haversineMeters(toSnap.point(), destination) > 1D) {
            path.add(toSnap.point());
        }
        path.add(destination);
        return dedupeConsecutive(path);
    }

    public double nearestRoadDistanceMeters(GeoPoint point) {
        if (point == null || roadPolylines.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double min = Double.MAX_VALUE;
        for (List<GeoPoint> polyline : roadPolylines) {
            for (int i = 1; i < polyline.size(); i++) {
                double dist = GeoPolygonUtils.distancePointToSegmentMeters(
                        point, polyline.get(i - 1), polyline.get(i));
                if (dist < min) {
                    min = dist;
                }
            }
        }
        return min;
    }

    private void load(ObjectMapper objectMapper, String configuredPath) {
        JsonNode root = readRoot(objectMapper, configuredPath);
        if (root == null) {
            log.warn("OSM pilot geo file not found; falling back to simplified grid zones");
            return;
        }
        for (JsonNode ring : root.path("buildings")) {
            List<GeoPoint> polygon = parseRing(ring);
            if (polygon.size() >= 3) {
                buildings.add(polygon);
            }
        }
        for (JsonNode polyline : root.path("roads")) {
            List<GeoPoint> line = parseRing(polyline);
            if (line.size() >= 2) {
                roadPolylines.add(line);
            }
        }
        for (List<GeoPoint> corridor : PilotGridRoads.corridors()) {
            if (corridor.size() >= 2 && !segmentCrossesAnyBuilding(corridor.get(0), corridor.get(1))) {
                roadPolylines.add(corridor);
            }
        }
    }

    private boolean segmentCrossesAnyBuilding(GeoPoint a, GeoPoint b) {
        for (List<GeoPoint> block : buildings) {
            if (GeoPolygonUtils.segmentIntersectsPolygon(a, b, block)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readRoot(ObjectMapper objectMapper, String configuredPath) {
        List<Path> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(Path.of(configuredPath));
        }
        String userDir = System.getProperty("user.dir", ".");
        candidates.add(Path.of(userDir, "data", "pilot_osm_geo.json"));
        candidates.add(Path.of(userDir, "..", "data", "pilot_osm_geo.json"));
        candidates.add(Path.of(userDir, "..", "..", "data", "pilot_osm_geo.json"));
        for (Path candidate : candidates) {
            try {
                Path resolved = candidate.normalize();
                if (Files.isRegularFile(resolved)) {
                    return objectMapper.readTree(resolved.toFile());
                }
            } catch (IOException ex) {
                log.debug("Failed reading OSM geo from {}: {}", candidate, ex.getMessage());
            }
        }
        try (InputStream stream = getClass().getResourceAsStream("/pilot_osm_geo.json")) {
            if (stream != null) {
                return objectMapper.readTree(stream);
            }
        } catch (IOException ex) {
            log.debug("Failed reading classpath pilot_osm_geo.json: {}", ex.getMessage());
        }
        return null;
    }

    private static List<GeoPoint> parseRing(JsonNode ring) {
        List<GeoPoint> points = new ArrayList<>();
        if (!ring.isArray()) {
            return points;
        }
        for (JsonNode node : ring) {
            if (node.isArray() && node.size() >= 2) {
                points.add(g(node.get(0).asDouble(), node.get(1).asDouble()));
            }
        }
        return points;
    }

    private void buildRoadGraph() {
        graphNodes.clear();
        adjacency.clear();
        for (List<GeoPoint> polyline : roadPolylines) {
            for (int i = 1; i < polyline.size(); i++) {
                GeoPoint a = polyline.get(i - 1);
                GeoPoint b = polyline.get(i);
                addEdge(a, b);
                addEdge(b, a);
            }
        }
    }

    private void addEdge(GeoPoint a, GeoPoint b) {
        if (segmentCrossesAnyBuilding(a, b)) {
            return;
        }
        String keyA = ensureNode(a);
        String keyB = ensureNode(b);
        double weight = GeoPolygonUtils.haversineMeters(a, b);
        if (weight <= 0D) {
            return;
        }
        adjacency.computeIfAbsent(keyA, ignored -> new ArrayList<>()).add(new Edge(keyB, weight));
    }

    /** 吸附点若落在路段中部，须连到该路段端点，否则 Dijkstra 无法到达。 */
    private void connectSnapToRoadSegment(String snapKey, GeoPoint snap, GeoPoint segStart, GeoPoint segEnd) {
        if (snapKey == null || snap == null || segStart == null || segEnd == null) {
            return;
        }
        String startKey = ensureNode(segStart);
        String endKey = ensureNode(segEnd);
        addUndirectedEdge(snapKey, startKey, GeoPolygonUtils.haversineMeters(snap, graphNodes.get(startKey)));
        addUndirectedEdge(snapKey, endKey, GeoPolygonUtils.haversineMeters(snap, graphNodes.get(endKey)));
    }

    private void addUndirectedEdge(String fromKey, String toKey, double weight) {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) {
            return;
        }
        double w = weight <= 0D ? 0.1D : weight;
        adjacency.computeIfAbsent(fromKey, ignored -> new ArrayList<>()).add(new Edge(toKey, w));
        adjacency.computeIfAbsent(toKey, ignored -> new ArrayList<>()).add(new Edge(fromKey, w));
    }

    private String ensureNode(GeoPoint point) {
        String exactKey = nodeKey(point);
        for (Map.Entry<String, GeoPoint> entry : graphNodes.entrySet()) {
            if (GeoPolygonUtils.haversineMeters(entry.getValue(), point) <= NODE_MERGE_METERS) {
                return entry.getKey();
            }
        }
        graphNodes.put(exactKey, point);
        adjacency.computeIfAbsent(exactKey, ignored -> new ArrayList<>());
        return exactKey;
    }

    private List<GeoPoint> dijkstra(String startKey, String endKey) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<Map.Entry<String, Double>> queue = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
        dist.put(startKey, 0D);
        queue.add(Map.entry(startKey, 0D));
        while (!queue.isEmpty()) {
            Map.Entry<String, Double> current = queue.poll();
            String nodeKey = current.getKey();
            if (nodeKey.equals(endKey)) {
                break;
            }
            if (current.getValue() > dist.getOrDefault(nodeKey, Double.MAX_VALUE)) {
                continue;
            }
            for (Edge edge : adjacency.getOrDefault(nodeKey, List.of())) {
                double alt = dist.get(nodeKey) + edge.weight();
                if (alt < dist.getOrDefault(edge.toKey(), Double.MAX_VALUE)) {
                    dist.put(edge.toKey(), alt);
                    prev.put(edge.toKey(), nodeKey);
                    queue.add(Map.entry(edge.toKey(), alt));
                }
            }
        }
        if (!prev.containsKey(endKey) && !startKey.equals(endKey)) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String cursor = endKey;
        keys.add(cursor);
        while (prev.containsKey(cursor)) {
            cursor = prev.get(cursor);
            keys.add(cursor);
        }
        List<String> ordered = new ArrayList<>(keys);
        java.util.Collections.reverse(ordered);
        List<GeoPoint> path = new ArrayList<>();
        for (String key : ordered) {
            path.add(graphNodes.get(key));
        }
        return path;
    }

    private static GeoPoint projectOnSegment(GeoPoint point, GeoPoint segStart, GeoPoint segEnd) {
        double px = point.longitude().doubleValue();
        double py = point.latitude().doubleValue();
        double ax = segStart.longitude().doubleValue();
        double ay = segStart.latitude().doubleValue();
        double bx = segEnd.longitude().doubleValue();
        double by = segEnd.latitude().doubleValue();
        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0D && dy == 0D) {
            return segStart;
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0D, Math.min(1D, t));
        return g(ax + t * dx, ay + t * dy);
    }

    private static List<GeoPoint> dedupeConsecutive(List<GeoPoint> path) {
        List<GeoPoint> result = new ArrayList<>();
        GeoPoint last = null;
        for (GeoPoint point : path) {
            if (last != null && GeoPolygonUtils.haversineMeters(last, point) < 0.5D) {
                continue;
            }
            result.add(point);
            last = point;
        }
        return result;
    }

    private static String nodeKey(GeoPoint point) {
        return String.format("%.6f,%.6f",
                point.longitude().doubleValue(), point.latitude().doubleValue());
    }

    static GeoPoint g(double lng, double lat) {
        return new GeoPoint(
                BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP));
    }

    public record OptionalSnap(GeoPoint point, String nodeKey, double distanceMeters,
                               GeoPoint segStart, GeoPoint segEnd) {
        static OptionalSnap empty() {
            return new OptionalSnap(null, null, Double.MAX_VALUE, null, null);
        }

        boolean isEmpty() {
            return point == null;
        }
    }

    private record Edge(String toKey, double weight) {
    }
}
