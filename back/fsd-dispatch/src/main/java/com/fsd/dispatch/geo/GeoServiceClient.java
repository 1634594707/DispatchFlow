package com.fsd.dispatch.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.GeoServiceProperties;
import java.math.BigDecimal;
import java.net.URI;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PostGIS 地理服务 HTTP 客户端（GEO-PG）。
 *
 * <p><b>契约</b>：任何异常、超时、熔断、非 2xx 都返回 {@link Optional#empty()}，
 * 绝不向调度热路径抛异常。调用方（{@link GeoQueryService}）负责降级到 Java 手算。
 *
 * <p><b>坐标系</b>：本客户端只发送 GCJ-02 坐标——与库内存储一致。
 * 若手上是 WGS-84（GPS 原始上报），必须先过 {@code Wgs84Gcj02Converter}；
 * 服务端对非 GCJ-02 输入直接 400，不会静默返回偏移结果。
 */
@Component
public class GeoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(GeoServiceClient.class);
    private static final String DATUM_GCJ02 = "GCJ-02";

    private final GeoServiceProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Object breakerLock = new Object();
    private int consecutiveFailures;
    private Instant openUntil = Instant.EPOCH;

    public GeoServiceClient(GeoServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 50L)))
                .build();
    }

    /** 围栏包含判定。返回命中的围栏编码；empty 表示服务不可用，调用方需降级。 */
    public Optional<List<String>> fenceContains(Long parkId, BigDecimal longitude, BigDecimal latitude) {
        JsonNode node = post("/geo/fence/contains", Map.of(
                "park_id", parkId,
                "lng", longitude.doubleValue(),
                "lat", latitude.doubleValue(),
                "datum", DATUM_GCJ02));
        if (node == null) {
            return Optional.empty();
        }
        List<String> codes = new ArrayList<>();
        node.path("fences").forEach(f -> codes.add(f.path("code").asText()));
        return Optional.of(codes);
    }

    /** 半径内最近站点。 */
    public Optional<List<StationHit>> nearbyStations(Long parkId, double lng, double lat,
                                                    int limit, double radiusM, boolean chargingOnly) {
        JsonNode node = post("/geo/station/nearby", Map.of(
                "park_id", parkId,
                "lng", lng,
                "lat", lat,
                "limit", limit,
                "radius_m", radiusM,
                "charging_only", chargingOnly,
                "datum", DATUM_GCJ02));
        if (node == null) {
            return Optional.empty();
        }
        List<StationHit> hits = new ArrayList<>();
        node.forEach(item -> hits.add(new StationHit(
                item.path("station_code").asText(),
                item.path("station_name").asText(),
                item.path("is_charging").asBoolean(),
                item.path("distance_m").asDouble())));
        return Optional.of(hits);
    }

    /** 最近路网节点吸附。empty = 服务不可用；Optional.of(emptyList) 语义由 present 与否区分。 */
    public Optional<String> nearestRoadNode(Long parkId, double lng, double lat, double radiusM) {
        JsonNode node = post("/geo/road-node/nearest", Map.of(
                "park_id", parkId,
                "lng", lng,
                "lat", lat,
                "radius_m", radiusM,
                "datum", DATUM_GCJ02));
        if (node == null) {
            return Optional.empty();
        }
        if (!node.path("found").asBoolean()) {
            return Optional.of("");
        }
        return Optional.of(node.path("node_code").asText());
    }

    private JsonNode post(String path, Map<String, Object> body) {
        if (!properties.isEnabled() || !breakerAllows()) {
            return null;
        }
        try {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // 4xx 是调用方错误（例如坐标系声明不对），记 warn 但不计入熔断：
                // 重试同一个错误请求没有意义。
                log.warn("地理服务返回非 2xx: path={} status={} body={}", path, response.statusCode(), response.body());
                return null;
            }
            recordSuccess();
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordFailure(path, ex);
            return null;
        } catch (Exception ex) {
            recordFailure(path, ex);
            return null;
        }
    }

    private boolean breakerAllows() {
        int threshold = properties.getCircuitBreakAfter();
        if (threshold <= 0) {
            return true;
        }
        synchronized (breakerLock) {
            return consecutiveFailures < threshold || Instant.now().isAfter(openUntil);
        }
    }

    private void recordSuccess() {
        synchronized (breakerLock) {
            consecutiveFailures = 0;
        }
    }

    private void recordFailure(String path, Exception ex) {
        int failures;
        synchronized (breakerLock) {
            consecutiveFailures++;
            failures = consecutiveFailures;
            if (failures >= properties.getCircuitBreakAfter() && properties.getCircuitBreakAfter() > 0) {
                openUntil = Instant.now().plusMillis(properties.getCircuitCooldownMs());
            }
        }
        log.warn("地理服务调用失败，走本地手算降级: path={} 连续失败={} 原因={}", path, failures, ex.toString());
    }

    /** 站点召回结果。 */
    public record StationHit(String stationCode, String stationName, boolean charging, double distanceMeters) {
    }
}
