package com.fsd.dispatch.geo.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.AmapProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 高德物流距离矩阵（N 起点 → 1 终点）。仅用于派单距离评分，不参与路网可达性判定。
 */
@Service
public class AmapLogisticsDistanceService {

    private static final Logger log = LoggerFactory.getLogger(AmapLogisticsDistanceService.class);

    private final AmapProperties amapProperties;
    private final ParkGeoTransformService parkGeoTransformService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AmapLogisticsDistanceService(AmapProperties amapProperties,
                                        ParkGeoTransformService parkGeoTransformService,
                                        ObjectMapper objectMapper) {
        this.amapProperties = amapProperties;
        this.parkGeoTransformService = parkGeoTransformService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, amapProperties.getLogistics().getTimeoutMs())))
                .build();
    }

    public boolean isAvailable() {
        AmapProperties.LogisticsConfig cfg = amapProperties.getLogistics();
        return cfg.isEnabled()
                && amapProperties.getWebServiceKey() != null
                && !amapProperties.getWebServiceKey().isBlank();
    }

    /**
     * @param origins  车辆 GCJ-02 坐标（与 origins 索引对齐）
     * @param destination 取货点 GCJ-02
     * @return 各起点到终点的驾车距离（米）；API 失败时返回 empty
     */
    public Optional<List<Double>> fetchDrivingDistancesMeters(List<GeoPoint> origins, GeoPoint destination) {
        if (!isAvailable() || origins == null || origins.isEmpty() || destination == null) {
            return Optional.empty();
        }
        try {
            String originsParam = joinOrigins(origins);
            String destParam = formatCoord(destination);
            String url = amapProperties.getLogistics().getBaseUrl()
                    + "?key=" + urlEncode(amapProperties.getWebServiceKey())
                    + "&origins=" + urlEncode(originsParam)
                    + "&destination=" + urlEncode(destParam)
                    + "&type=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(amapProperties.getLogistics().getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Amap distance matrix HTTP {}: {}", response.statusCode(), truncate(response.body()));
                return Optional.empty();
            }
            return parseDistances(response.body(), origins.size());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Amap distance matrix call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 将园区 schematic 坐标转为 GCJ-02（供矩阵请求）。 */
    public Optional<GeoPoint> toGcj02(BigDecimal parkX, BigDecimal parkY) {
        return parkGeoTransformService.toGcj02(parkX, parkY);
    }

    /**
     * 批量 N-1：多车 → 单站点，返回 vehicleIndex → 混合距离（px 等价）。
     * 混合公式：parkDistance * (1-w) + geoMetersToPx(geoMeters) * w
     */
    public Map<Integer, Double> blendDistances(List<GeoPoint> vehicleGeoPoints,
                                               GeoPoint destinationGeo,
                                               List<Double> parkDistancesPx,
                                               double blendWeight) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (vehicleGeoPoints == null || parkDistancesPx == null) {
            return result;
        }
        double w = Math.max(0D, Math.min(1D, blendWeight));
        Optional<List<Double>> geoMeters = fetchDrivingDistancesMeters(vehicleGeoPoints, destinationGeo);
        for (int i = 0; i < parkDistancesPx.size(); i++) {
            double parkPx = parkDistancesPx.get(i) == null ? Double.MAX_VALUE : parkDistancesPx.get(i);
            if (w <= 0D || geoMeters.isEmpty()) {
                result.put(i, parkPx);
                continue;
            }
            List<Double> meters = geoMeters.get();
            double geoPx = i < meters.size() ? metersToParkPx(meters.get(i)) : parkPx;
            result.put(i, parkPx * (1D - w) + geoPx * w);
        }
        return result;
    }

    /** 米 → 园区 px（与 ParkGeoTransform 比例一致，用于评分量级对齐）。 */
    public double metersToParkPx(double meters) {
        if (meters <= 0 || Double.isInfinite(meters)) {
            return Double.MAX_VALUE;
        }
        // 默认 2400m 宽 / 1200px → 2m/px
        return meters / 2.0;
    }

    private Optional<List<Double>> parseDistances(String body, int expectedSize) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            log.warn("Amap distance matrix status={} info={}", root.path("status").asText(), root.path("info").asText());
            return Optional.empty();
        }
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return Optional.empty();
        }
        List<Double> distances = new ArrayList<>(expectedSize);
        for (JsonNode item : results) {
            String distanceText = item.path("distance").asText("-1");
            try {
                distances.add(Double.parseDouble(distanceText));
            } catch (NumberFormatException ex) {
                distances.add(Double.MAX_VALUE);
            }
        }
        while (distances.size() < expectedSize) {
            distances.add(Double.MAX_VALUE);
        }
        return Optional.of(distances);
    }

    private static String joinOrigins(List<GeoPoint> origins) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < origins.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(formatCoord(origins.get(i)));
        }
        return sb.toString();
    }

    private static String formatCoord(GeoPoint point) {
        return point.longitude().toPlainString() + ',' + point.latitude().toPlainString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }
}
