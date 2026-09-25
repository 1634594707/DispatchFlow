package com.fsd.dispatch.geo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.GeoServiceProperties;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.mapper.StationMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 空间查询统一入口（GEO-PG）：PostGIS 优先，任何不可用都降级回 Java 手算。
 *
 * <p><b>接线点</b>：本服务是独立可用的新路径，尚未替换 {@code GeofenceBreachServiceImpl}
 * 第 98 行与 {@code StationMapper} 侧的既有调用。当初的切换步骤与风险记录在
 * {@code docs/实施记录-PostGIS地理服务-2026-09-20.md}，该文档已删除，按路径取回原文用
 * {@code git log --diff-filter=D -- 'docs/实施记录-PostGIS*'}。
 *
 * <p>{@code fsd.geo-service.enabled=false}（默认）时，本类的行为与直接调用
 * {@code GeoPolygonUtils} 完全一致——不产生任何 HTTP 请求。
 */
@Service
public class GeoQueryService {

    private static final Logger log = LoggerFactory.getLogger(GeoQueryService.class);
    private static final String ACTIVE = "ACTIVE";

    private final GeoServiceClient client;
    private final GeoServiceProperties properties;
    private final ParkGeofenceMapper geofenceMapper;
    private final StationMapper stationMapper;
    private final ObjectMapper objectMapper;

    public GeoQueryService(GeoServiceClient client,
                           GeoServiceProperties properties,
                           ParkGeofenceMapper geofenceMapper,
                           StationMapper stationMapper,
                           ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.geofenceMapper = geofenceMapper;
        this.stationMapper = stationMapper;
        this.objectMapper = objectMapper;
    }

    /** 点是否落在任一 ACTIVE 围栏内。 */
    public boolean containsInAnyFence(Long parkId, BigDecimal longitude, BigDecimal latitude) {
        Optional<List<String>> remote = client.fenceContains(parkId, longitude, latitude);
        if (remote.isPresent()) {
            return !remote.get().isEmpty();
        }
        for (ParkGeofenceEntity fence : activeFences(parkId)) {
            if (GeoPolygonUtils.contains(parsePolygon(fence.getPolygonJson()), longitude, latitude)) {
                return true;
            }
        }
        return false;
    }

    /** 半径内最近 N 个站点，按距离升序。 */
    public List<GeoServiceClient.StationHit> nearestStations(Long parkId, double lng, double lat,
                                                            int limit, boolean chargingOnly) {
        double radius = properties.getDefaultRadiusM();
        Optional<List<GeoServiceClient.StationHit>> remote =
                client.nearbyStations(parkId, lng, lat, limit, radius, chargingOnly);
        if (remote.isPresent()) {
            return remote.get();
        }
        List<GeoServiceClient.StationHit> hits = new ArrayList<>();
        for (StationEntity station : activeStations(parkId)) {
            if (station.getCoordLng() == null || station.getCoordLat() == null) {
                continue;
            }
            if (chargingOnly && !isCharging(station)) {
                continue;
            }
            double distance = GeoPolygonUtils.haversineMeters(
                    new ParkGeoTransformService.GeoPoint(BigDecimal.valueOf(lng), BigDecimal.valueOf(lat)),
                    new ParkGeoTransformService.GeoPoint(station.getCoordLng(), station.getCoordLat()));
            if (distance <= radius) {
                hits.add(new GeoServiceClient.StationHit(
                        station.getStationCode(), station.getStationName(), isCharging(station), distance));
            }
        }
        hits.sort(Comparator.comparingDouble(GeoServiceClient.StationHit::distanceMeters));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    /**
     * 最近路网节点吸附。
     *
     * @return {@code null} 表示半径内无节点；否则节点编码
     */
    public String nearestRoadNodeCode(Long parkId, double lng, double lat, double radiusM) {
        Optional<String> remote = client.nearestRoadNode(parkId, lng, lat, radiusM);
        if (remote.isPresent()) {
            return remote.get().isEmpty() ? null : remote.get();
        }
        // 本地降级路径不做节点吸附：ParkRoadGraph 已在内存里持有图结构，
        // 由寻路侧的吸附逻辑负责，这里不重复实现一份会漂移的近似。
        log.debug("地理服务不可用，节点吸附回退到内存图路径");
        return null;
    }

    private List<ParkGeofenceEntity> activeFences(Long parkId) {
        return geofenceMapper.selectList(Wrappers.<ParkGeofenceEntity>lambdaQuery()
                .eq(ParkGeofenceEntity::getParkId, parkId)
                .eq(ParkGeofenceEntity::getStatus, ACTIVE)
                .eq(ParkGeofenceEntity::getDeleted, 0));
    }

    private List<StationEntity> activeStations(Long parkId) {
        return stationMapper.selectList(Wrappers.<StationEntity>lambdaQuery()
                .eq(StationEntity::getParkId, parkId)
                .eq(StationEntity::getStatus, ACTIVE)
                .eq(StationEntity::getDeleted, 0));
    }

    private static boolean isCharging(StationEntity station) {
        String code = station.getStationCode() == null ? "" : station.getStationCode().toUpperCase();
        String type = station.getStationType() == null ? "" : station.getStationType().toUpperCase();
        return code.contains("CHG") || code.contains("CHARGE") || type.contains("CHARG");
    }

    /** 解析 {@code [[lng,lat],...]} 形态的 GCJ-02 多边形。 */
    private List<double[]> parsePolygon(String polygonJson) {
        if (polygonJson == null || polygonJson.isBlank()) {
            return List.of();
        }
        try {
            List<List<Number>> raw = objectMapper.readValue(polygonJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, objectMapper.getTypeFactory().constructCollectionType(List.class, Number.class)));
            List<double[]> ring = new ArrayList<>(raw.size());
            for (List<Number> point : raw) {
                if (point.size() >= 2) {
                    ring.add(new double[] {point.get(0).doubleValue(), point.get(1).doubleValue()});
                }
            }
            return ring;
        } catch (Exception ex) {
            log.warn("围栏多边形解析失败，按无围栏处理: {}", ex.toString());
            return List.of();
        }
    }
}
