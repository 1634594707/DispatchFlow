package com.fsd.dispatch.service.impl;

import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.GeofenceBreachService;
import com.fsd.vehicle.entity.VehicleEntity;
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

    private final ParkGeofenceMapper geofenceMapper;
    private final DispatchExceptionService dispatchExceptionService;
    private final DispatchAutomationRuleService automationRuleService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${fsd.automation.default-park-id:1}")
    private Long defaultParkId;

    public GeofenceBreachServiceImpl(ParkGeofenceMapper geofenceMapper,
                                     DispatchExceptionService dispatchExceptionService,
                                     DispatchAutomationRuleService automationRuleService,
                                     StringRedisTemplate stringRedisTemplate) {
        this.geofenceMapper = geofenceMapper;
        this.dispatchExceptionService = dispatchExceptionService;
        this.automationRuleService = automationRuleService;
        this.stringRedisTemplate = stringRedisTemplate;
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

    private void evaluateFence(VehicleEntity vehicle, ParkGeofenceEntity fence,
                               BigDecimal longitude, BigDecimal latitude) {
        boolean inside = GeoPolygonUtils.contains(parsePolygon(fence.getPolygonJson()), longitude, latitude);
        String breachType = resolveBreachType(fence.getFenceType(), inside);
        if (breachType == null || !markIfFirstBreach(vehicle.getId(), fence.getId(), breachType)) {
            return;
        }
        String message = buildMessage(vehicle, fence, breachType);
        dispatchExceptionService.recordVehicleException(vehicle.getId(), breachType, message);
        automationRuleService.evaluateGeofenceBreach(fence.getParkId(), vehicle, fence.getFenceCode(), breachType);
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
