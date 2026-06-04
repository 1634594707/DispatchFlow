package com.fsd.dispatch.geo.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.AmapProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteFollower;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RoadRouteSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Qualifier("amap")
public class AmapRoadRouteService implements RoadRouteService {

    private static final Logger log = LoggerFactory.getLogger(AmapRoadRouteService.class);

    private final AmapProperties amapProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong fallbackCount = new AtomicLong(0);
    private final AtomicLong amapSuccessCount = new AtomicLong(0);

    public AmapRoadRouteService(AmapProperties amapProperties, ObjectMapper objectMapper) {
        this.amapProperties = amapProperties;
        this.objectMapper = objectMapper;
        AmapProperties.DrivingConfig cfg = amapProperties.getDriving();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, cfg.getTimeoutMs())))
                .build();
    }

    @Override
    public boolean isAvailable() {
        AmapProperties.DrivingConfig cfg = amapProperties.getDriving();
        return cfg.isEnabled()
                && amapProperties.getWebServiceKey() != null
                && !amapProperties.getWebServiceKey().isBlank();
    }

    @Override
    public RoadRouteResult planDrivingRoute(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return fallbackStraightLine(origin, destination);
        }
        String cacheKey = cacheKey(origin, destination);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.result;
        }
        if (!isAvailable()) {
            RoadRouteResult fallback = fallbackStraightLine(origin, destination);
            cache.put(cacheKey, new CacheEntry(fallback, Instant.now().plusSeconds(300)));
            return fallback;
        }
        try {
            Optional<RoadRouteResult> fromApi = fetchFromAmap(origin, destination);
            if (fromApi.isPresent()) {
                amapSuccessCount.incrementAndGet();
                RoadRouteResult result = fromApi.get();
                long ttl = amapProperties.getDriving().getCacheTtlSeconds();
                cache.put(cacheKey, new CacheEntry(result, Instant.now().plusSeconds(Math.max(60, ttl))));
                return result;
            }
        } catch (Exception ex) {
            log.warn("Amap driving route failed: {}", ex.getMessage());
        }
        RoadRouteResult fallback = fallbackStraightLine(origin, destination);
        cache.put(cacheKey, new CacheEntry(fallback, Instant.now().plusSeconds(300)));
        return fallback;
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }

    public long getAmapSuccessCount() {
        return amapSuccessCount.get();
    }

    private Optional<RoadRouteResult> fetchFromAmap(GeoPoint origin, GeoPoint destination) throws Exception {
        AmapProperties.DrivingConfig cfg = amapProperties.getDriving();
        String originParam = formatCoord(origin);
        String destParam = formatCoord(destination);
        String url = cfg.getBaseUrl()
                + "?key=" + urlEncode(amapProperties.getWebServiceKey())
                + "&origin=" + urlEncode(originParam)
                + "&destination=" + urlEncode(destParam)
                + "&extensions=base";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Amap driving HTTP {}: {}", response.statusCode(), truncate(response.body()));
            return Optional.empty();
        }
        return parseDrivingResponse(response.body());
    }

    private Optional<RoadRouteResult> parseDrivingResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            log.warn("Amap driving status={} info={}", root.path("status").asText(), root.path("info").asText());
            return Optional.empty();
        }
        JsonNode path = root.path("route").path("paths").path(0);
        if (path.isMissingNode()) {
            return Optional.empty();
        }
        double distance = path.path("distance").asDouble(0D);
        List<GeoPoint> points = new ArrayList<>();
        for (JsonNode step : path.path("steps")) {
            String polyline = step.path("polyline").asText("");
            points.addAll(parsePolyline(polyline));
        }
        if (points.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new RoadRouteResult(dedupe(points), distance, RoadRouteSource.AMAP));
    }

    private static List<GeoPoint> parsePolyline(String polyline) {
        List<GeoPoint> points = new ArrayList<>();
        if (polyline == null || polyline.isBlank()) {
            return points;
        }
        String[] pairs = polyline.split(";");
        for (String pair : pairs) {
            String[] parts = pair.split(",");
            if (parts.length >= 2) {
                try {
                    double lng = Double.parseDouble(parts[0].trim());
                    double lat = Double.parseDouble(parts[1].trim());
                    points.add(new GeoPoint(scale(lng), scale(lat)));
                } catch (NumberFormatException ignored) {
                    // skip malformed vertex
                }
            }
        }
        return points;
    }

    private static List<GeoPoint> dedupe(List<GeoPoint> points) {
        List<GeoPoint> result = new ArrayList<>();
        GeoPoint last = null;
        for (GeoPoint point : points) {
            if (last == null
                    || last.longitude().compareTo(point.longitude()) != 0
                    || last.latitude().compareTo(point.latitude()) != 0) {
                result.add(point);
                last = point;
            }
        }
        return result;
    }

    private RoadRouteResult fallbackStraightLine(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return new RoadRouteResult(List.of(), 0D, RoadRouteSource.STRAIGHT_LINE);
        }
        List<GeoPoint> line = List.of(origin, destination);
        double meters = RoadRouteFollower.fromPolyline(line).totalMeters();
        fallbackCount.incrementAndGet();
        return new RoadRouteResult(line, meters, RoadRouteSource.STRAIGHT_LINE);
    }

    private static String cacheKey(GeoPoint origin, GeoPoint destination) {
        return origin.longitude().toPlainString() + ',' + origin.latitude().toPlainString()
                + "->" + destination.longitude().toPlainString() + ',' + destination.latitude().toPlainString();
    }

    private static String formatCoord(GeoPoint point) {
        return point.longitude().toPlainString() + ',' + point.latitude().toPlainString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    private record CacheEntry(RoadRouteResult result, Instant expiresAt) {
    }
}
