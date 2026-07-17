package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.GeofenceBreachService;
import com.fsd.common.exception.BusinessException;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class GeofenceBreachServiceImpl implements GeofenceBreachService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceBreachServiceImpl.class);
    private static final Duration BREACH_COOLDOWN = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "geofence:breach:";
    /** 阶段六 6.2：默认 GPS 缓冲距离，作为围栏未配置 buffer_meters 时的兜底值。 */
    private static final double DEFAULT_GPS_BUFFER_METERS = 15.0;
    /** 阶段六 6.1：默认响应级别，作为围栏未配置 response_level 时的兜底值。 */
    private static final String DEFAULT_RESPONSE_LEVEL = "WARN";

    private final ParkGeofenceMapper geofenceMapper;
    private final DispatchExceptionService dispatchExceptionService;
    private final DispatchAutomationRuleService automationRuleService;
    private final StringRedisTemplate stringRedisTemplate;
    private final VehicleService vehicleService;
    private final DispatchTaskMapper dispatchTaskMapper;

    @Value("${fsd.automation.default-park-id:1}")
    private Long defaultParkId;

    public GeofenceBreachServiceImpl(ParkGeofenceMapper geofenceMapper,
                                     DispatchExceptionService dispatchExceptionService,
                                     DispatchAutomationRuleService automationRuleService,
                                     StringRedisTemplate stringRedisTemplate,
                                     VehicleService vehicleService,
                                     DispatchTaskMapper dispatchTaskMapper) {
        this.geofenceMapper = geofenceMapper;
        this.dispatchExceptionService = dispatchExceptionService;
        this.automationRuleService = automationRuleService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.vehicleService = vehicleService;
        this.dispatchTaskMapper = dispatchTaskMapper;
    }

    @Override
    public void evaluateVehiclePosition(Long parkId, VehicleEntity vehicle, BigDecimal longitude, BigDecimal latitude) {
        if (vehicle == null || longitude == null || latitude == null) {
            return;
        }
        Long effectiveParkId = parkId != null ? parkId : defaultParkId;
        List<ParkGeofenceEntity> fences = geofenceMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ParkGeofenceEntity>lambdaQuery()
                        .eq(ParkGeofenceEntity::getParkId, effectiveParkId)
                        .eq(ParkGeofenceEntity::getStatus, "ACTIVE")
                        .eq(ParkGeofenceEntity::getDeleted, 0));
        for (ParkGeofenceEntity fence : fences) {
            evaluateFence(vehicle, fence, longitude, latitude);
        }
    }

    @Override
    public boolean isWithinAllowedArea(Long vehicleId, BigDecimal longitude, BigDecimal latitude) {
        if (vehicleId == null || longitude == null || latitude == null) {
            return true;
        }
        try {
            vehicleService.getById(vehicleId);
        } catch (BusinessException ignored) {
            return true;
        }
        List<ParkGeofenceEntity> fences = geofenceMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ParkGeofenceEntity>lambdaQuery()
                        .eq(ParkGeofenceEntity::getParkId, defaultParkId)
                        .eq(ParkGeofenceEntity::getStatus, "ACTIVE")
                        .eq(ParkGeofenceEntity::getFenceType, "BOUNDARY")
                        .eq(ParkGeofenceEntity::getDeleted, 0));
        if (fences.isEmpty()) {
            return true;
        }
        for (ParkGeofenceEntity fence : fences) {
            if (GeoPolygonUtils.contains(parsePolygon(fence.getPolygonJson()), longitude, latitude)) {
                return true;
            }
        }
        return false;
    }

    private void evaluateFence(VehicleEntity vehicle, ParkGeofenceEntity fence,
                               BigDecimal longitude, BigDecimal latitude) {
        List<double[]> polygon = parsePolygon(fence.getPolygonJson());
        boolean inside = GeoPolygonUtils.contains(polygon, longitude, latitude);
        String breachType = resolveBreachType(fence.getFenceType(), inside);
        if (breachType == null || !markIfFirstBreach(vehicle.getId(), fence.getId(), breachType)) {
            return;
        }
        // 阶段六 6.2：使用围栏独立的 buffer_meters 进行 GPS 缓冲判定，替代原硬编码常量。
        double bufferMeters = resolveBufferMeters(fence);
        if ("GEOFENCE_EXIT".equals(breachType) && isWithinGpsBuffer(polygon, longitude, latitude, bufferMeters)) {
            return;
        }
        // 围栏"无任务不告警"：IDLE 车辆越过 BOUNDARY 围栏时降级为 INFO 日志，
        // 不记录异常、不触发自动化规则。显著减少异常队列 GEOFENCE_EXIT 噪音。
        if (isIdleBoundaryExit(vehicle, fence, breachType)) {
            log.info("GEOFENCE_EXIT suppressed (IDLE vehicle, no active task): vehicle={}, fence={}",
                    vehicle.getVehicleCode(), fence.getFenceCode());
            return;
        }
        // 阶段六 6.1：按围栏 response_level 分级响应。
        String responseLevel = resolveResponseLevel(fence);
        String message = buildMessage(vehicle, fence, breachType);
        if ("INFO".equals(responseLevel)) {
            // INFO：仅记录日志，不写异常、不触发自动化规则。
            log.info("Geofence breach (INFO level): vehicle={}, fence={}, breachType={}",
                    vehicle.getVehicleCode(), fence.getFenceCode(), breachType);
            return;
        }
        Long taskId = findCurrentTaskId(vehicle.getId());
        if (taskId != null) {
            dispatchExceptionService.recordException(taskId, null, vehicle.getId(), breachType, message);
        } else {
            dispatchExceptionService.recordVehicleException(vehicle.getId(), breachType, message);
        }
        automationRuleService.evaluateGeofenceBreach(fence.getParkId(), vehicle, fence.getFenceCode(), breachType);
        if ("BLOCK".equals(responseLevel)) {
            // BLOCK：触发紧急停车，将车辆置为 UNAVAILABLE，阻止后续派单。
            log.warn("Geofence breach (BLOCK level) triggering emergency stop: vehicle={}, fence={}, breachType={}",
                    vehicle.getVehicleCode(), fence.getFenceCode(), breachType);
            vehicleService.markUnavailable(vehicle.getId());
        }
    }

    /**
     * 阶段六 6.1：解析围栏响应级别，缺省时按围栏类型给出合理默认值
     * （RESTRICTED→BLOCK，其余→WARN），并兜底 DEFAULT_RESPONSE_LEVEL。
     */
    private String resolveResponseLevel(ParkGeofenceEntity fence) {
        String level = fence.getResponseLevel();
        if (level != null && !level.isBlank()) {
            return level.trim().toUpperCase(Locale.ROOT);
        }
        String fenceType = fence.getFenceType() == null ? "" : fence.getFenceType().trim().toUpperCase(Locale.ROOT);
        if ("RESTRICTED".equals(fenceType)) {
            return "BLOCK";
        }
        return DEFAULT_RESPONSE_LEVEL;
    }

    /**
     * 阶段六 6.2：解析围栏 GPS 缓冲距离，缺省时回退到 DEFAULT_GPS_BUFFER_METERS。
     */
    private double resolveBufferMeters(ParkGeofenceEntity fence) {
        if (fence.getBufferMeters() != null) {
            double v = fence.getBufferMeters().doubleValue();
            if (v > 0D) {
                return v;
            }
        }
        return DEFAULT_GPS_BUFFER_METERS;
    }

    /**
     * 判定是否为"IDLE 车辆越过 BOUNDARY 围栏"——此类事件降级为 INFO 不记录异常。
     * 仅对 BOUNDARY 围栏生效；RESTRICTED 围栏进入仍然需要告警（安全风险）。
     */
    private boolean isIdleBoundaryExit(VehicleEntity vehicle, ParkGeofenceEntity fence, String breachType) {
        if (!"GEOFENCE_EXIT".equals(breachType)) {
            return false;
        }
        String fenceType = fence.getFenceType() == null ? "" : fence.getFenceType().trim().toUpperCase(Locale.ROOT);
        if (!"BOUNDARY".equals(fenceType)) {
            return false;
        }
        String dispatchStatus = vehicle.getDispatchStatus();
        boolean idle = dispatchStatus == null
                || "IDLE".equalsIgnoreCase(dispatchStatus);
        if (!idle) {
            return false;
        }
        // 二次确认：无活跃任务才降级，避免 IDLE 状态滞后的在途车辆被漏报
        return findCurrentTaskId(vehicle.getId()) == null;
    }

    private boolean isWithinGpsBuffer(List<double[]> polygon, BigDecimal longitude, BigDecimal latitude, double bufferMeters) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        GeoPoint point = new GeoPoint(longitude, latitude);
        for (int i = 0; i < polygon.size(); i++) {
            double[] curr = polygon.get(i);
            double[] next = polygon.get((i + 1) % polygon.size());
            GeoPoint segStart = new GeoPoint(BigDecimal.valueOf(curr[0]), BigDecimal.valueOf(curr[1]));
            GeoPoint segEnd = new GeoPoint(BigDecimal.valueOf(next[0]), BigDecimal.valueOf(next[1]));
            double distance = GeoPolygonUtils.distancePointToSegmentMeters(point, segStart, segEnd);
            if (distance < bufferMeters) {
                return true;
            }
        }
        return false;
    }

    private Long findCurrentTaskId(Long vehicleId) {
        Page<DispatchTaskEntity> page = dispatchTaskMapper.selectPage(new Page<>(1, 1, false),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<DispatchTaskEntity>lambdaQuery()
                        .eq(DispatchTaskEntity::getVehicleId, vehicleId)
                        .in(DispatchTaskEntity::getStatus, "ASSIGNED", "IN_PROGRESS", "GOING_TO_PICKUP")
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .orderByDesc(DispatchTaskEntity::getAssignTime));
        List<DispatchTaskEntity> records = page.getRecords();
        DispatchTaskEntity task = records.isEmpty() ? null : records.get(0);
        return task != null ? task.getId() : null;
    }

    private static String resolveBreachType(String fenceType, boolean inside) {
        String normalized = fenceType == null ? "BOUNDARY" : fenceType.trim().toUpperCase(Locale.ROOT);
        if ("RESTRICTED".equals(normalized)) {
            return inside ? "GEOFENCE_ENTER" : null;
        }
        return inside ? null : "GEOFENCE_EXIT";
    }

    private static String buildMessage(VehicleEntity vehicle, ParkGeofenceEntity fence, String breachType) {
        String action = "GEOFENCE_EXIT".equals(breachType) ? "驶出" : "进入";
        return "车辆 " + vehicle.getVehicleCode() + " " + action + "围栏「" + fence.getFenceName() + "」(" + fence.getFenceCode() + ")";
    }

    private boolean markIfFirstBreach(Long vehicleId, Long fenceId, String breachType) {
        String key = KEY_PREFIX + vehicleId + ":" + fenceId + ":" + breachType;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", BREACH_COOLDOWN);
        return Boolean.TRUE.equals(acquired);
    }

    private static List<double[]> parsePolygon(String polygonJson) {
        List<double[]> vertices = new ArrayList<>();
        if (polygonJson == null || polygonJson.isBlank()) {
            return vertices;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<List<Number>> raw = mapper.readValue(polygonJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
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
