package com.fsd.dispatch.service.impl;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class GeofenceBreachServiceImpl implements GeofenceBreachService {

    private static final Duration BREACH_COOLDOWN = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "geofence:breach:";
    private static final double GPS_BUFFER_METERS = 15.0;

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
        if ("GEOFENCE_EXIT".equals(breachType) && isWithinGpsBuffer(polygon, longitude, latitude)) {
            return;
        }
        String message = buildMessage(vehicle, fence, breachType);
        Long taskId = findCurrentTaskId(vehicle.getId());
        if (taskId != null) {
            dispatchExceptionService.recordException(taskId, null, vehicle.getId(), breachType, message);
        } else {
            dispatchExceptionService.recordVehicleException(vehicle.getId(), breachType, message);
        }
        automationRuleService.evaluateGeofenceBreach(fence.getParkId(), vehicle, fence.getFenceCode(), breachType);
    }

    private boolean isWithinGpsBuffer(List<double[]> polygon, BigDecimal longitude, BigDecimal latitude) {
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
            if (distance < GPS_BUFFER_METERS) {
                return true;
            }
        }
        return false;
    }

    private Long findCurrentTaskId(Long vehicleId) {
        DispatchTaskEntity task = dispatchTaskMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<DispatchTaskEntity>lambdaQuery()
                        .eq(DispatchTaskEntity::getVehicleId, vehicleId)
                        .in(DispatchTaskEntity::getStatus, "ASSIGNED", "IN_PROGRESS", "GOING_TO_PICKUP")
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .orderByDesc(DispatchTaskEntity::getAssignTime)
                        .last("LIMIT 1"));
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
