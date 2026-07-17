package com.fsd.dispatch.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.mapper.StationMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 围栏多边形自检工具（阶段六 6.3）。
 *
 * <p>定期校验所有 ACTIVE 围栏是否包含至少 1 个站点（基于 GCJ-02 坐标），
 * 报告"空围栏"列表。空围栏通常是多边形绘制错误或站点坐标漂移导致，
 * 可能造成围栏告警误报或漏报。
 *
 * <p>验收标准：每月自动巡检报告。</p>
 */
@Component
public class GeofenceSelfCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(GeofenceSelfCheckScheduler.class);

    private final ParkGeofenceMapper geofenceMapper;
    private final StationMapper stationMapper;

    public GeofenceSelfCheckScheduler(ParkGeofenceMapper geofenceMapper, StationMapper stationMapper) {
        this.geofenceMapper = geofenceMapper;
        this.stationMapper = stationMapper;
    }

    /**
     * 每月 1 日 03:00 执行一次围栏自检。
     * 可通过 fsd.automation.geofence-self-check-cron 覆盖 cron 表达式。
     */
    @Scheduled(cron = "${fsd.automation.geofence-self-check-cron:0 0 3 1 * ?}")
    public void selfCheck() {
        List<ParkGeofenceEntity> fences = geofenceMapper.selectList(
                Wrappers.<ParkGeofenceEntity>lambdaQuery()
                        .eq(ParkGeofenceEntity::getStatus, "ACTIVE")
                        .eq(ParkGeofenceEntity::getDeleted, 0));
        if (fences.isEmpty()) {
            log.info("Geofence self-check: no ACTIVE fences found, skip.");
            return;
        }
        List<StationEntity> stations = stationMapper.selectList(
                Wrappers.<StationEntity>lambdaQuery()
                        .eq(StationEntity::getDeleted, 0)
                        .isNotNull(StationEntity::getCoordLng)
                        .isNotNull(StationEntity::getCoordLat));
        List<String> emptyFences = new ArrayList<>();
        List<String> invalidPolygonFences = new ArrayList<>();
        int checked = 0;
        for (ParkGeofenceEntity fence : fences) {
            checked++;
            List<double[]> polygon = parsePolygon(fence.getPolygonJson());
            if (polygon.size() < 3) {
                invalidPolygonFences.add(fence.getFenceCode() + " (id=" + fence.getId() + ")");
                continue;
            }
            boolean containsStation = false;
            for (StationEntity station : stations) {
                if (GeoPolygonUtils.contains(polygon, station.getCoordLng(), station.getCoordLat())) {
                    containsStation = true;
                    break;
                }
            }
            if (!containsStation) {
                emptyFences.add(fence.getFenceCode() + " (id=" + fence.getId()
                        + ", parkId=" + fence.getParkId() + ")");
            }
        }
        if (emptyFences.isEmpty() && invalidPolygonFences.isEmpty()) {
            log.info("Geofence self-check PASS: checked={} fences, all contain >=1 station.", checked);
            return;
        }
        if (!emptyFences.isEmpty()) {
            log.warn("Geofence self-check found {} empty fence(s) (no station inside): {}",
                    emptyFences.size(), emptyFences);
        }
        if (!invalidPolygonFences.isEmpty()) {
            log.warn("Geofence self-check found {} fence(s) with invalid polygon (<3 vertices): {}",
                    invalidPolygonFences.size(), invalidPolygonFences);
        }
    }

    /** 复用 GeofenceBreachServiceImpl 中的多边形解析逻辑。 */
    private static List<double[]> parsePolygon(String polygonJson) {
        List<double[]> vertices = new ArrayList<>();
        if (polygonJson == null || polygonJson.isBlank()) {
            return vertices;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<List<Number>> raw = mapper.readValue(polygonJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            for (List<Number> point : raw) {
                if (point != null && point.size() >= 2) {
                    vertices.add(new double[] {point.get(0).doubleValue(), point.get(1).doubleValue()});
                }
            }
        } catch (java.io.IOException ignored) {
            return List.of();
        }
        return vertices;
    }
}
