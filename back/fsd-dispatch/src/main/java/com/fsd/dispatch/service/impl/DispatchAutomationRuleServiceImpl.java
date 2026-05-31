package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.entity.DispatchAutomationRuleEntity;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.mapper.DispatchAutomationRuleMapper;
import com.fsd.dispatch.fleet.real.RealFleetSwapCoordinator;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DispatchAutomationRuleServiceImpl implements DispatchAutomationRuleService {

    private final DispatchAutomationRuleMapper ruleMapper;
    private final PeakModeService peakModeService;
    private final DispatchExceptionService dispatchExceptionService;
    private final RealFleetSwapCoordinator realFleetSwapCoordinator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DispatchAutomationRuleServiceImpl(DispatchAutomationRuleMapper ruleMapper,
                                               PeakModeService peakModeService,
                                               DispatchExceptionService dispatchExceptionService,
                                               RealFleetSwapCoordinator realFleetSwapCoordinator) {
        this.ruleMapper = ruleMapper;
        this.peakModeService = peakModeService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.realFleetSwapCoordinator = realFleetSwapCoordinator;
    }

    @Override
    public double resolvePeakDistanceFactor(Long parkId, double defaultFactor) {
        if (!peakModeService.isPeakMode(parkId)) {
            return 1.0;
        }
        for (DispatchAutomationRuleEntity rule : enabledRules(parkId)) {
            if (!"PEAK_MODE".equalsIgnoreCase(rule.getConditionType())) {
                continue;
            }
            if (!"PEAK".equalsIgnoreCase(rule.getConditionValue())) {
                continue;
            }
            if (!"BOOST_DISPATCH".equalsIgnoreCase(rule.getActionType())) {
                continue;
            }
            return readDoubleParam(rule.getActionParamsJson(), "weightDistanceFactor", defaultFactor);
        }
        return defaultFactor;
    }

    @Override
    public void evaluateSimulationTick(Long parkId, VehicleEntity vehicle, SimulationMotionState state) {
        if (vehicle == null || state == null) {
            return;
        }
        int soc = vehicle.getBatteryLevel() == null ? 100 : vehicle.getBatteryLevel();
        for (DispatchAutomationRuleEntity rule : enabledRules(parkId)) {
            if ("SOC_BELOW".equalsIgnoreCase(rule.getConditionType())) {
                int threshold = parseInt(rule.getConditionValue(), 20);
                if (soc >= threshold) {
                    continue;
                }
                if ("CREATE_CHARGE_TASK".equalsIgnoreCase(rule.getActionType()) && isIdleLike(state.stage)) {
                    state.stage = "WAIT_CHARGING";
                }
            }
            if ("PEAK_MODE".equalsIgnoreCase(rule.getConditionType())
                    && peakModeService.isPeakMode(parkId)
                    && "SET_PEAK_MODE".equalsIgnoreCase(rule.getActionType())) {
                // Peak already active; no-op marker for audit trail compatibility
            }
        }
    }

    @Override
    public boolean evaluateFleetEnergyRules(Long parkId, VehicleEntity vehicle, String runtimeStage) {
        if (vehicle == null || !isIdleLike(runtimeStage)) {
            return false;
        }
        int soc = vehicle.getBatteryLevel() == null ? 100 : vehicle.getBatteryLevel();
        for (DispatchAutomationRuleEntity rule : enabledRules(parkId)) {
            if (!"SOC_BELOW".equalsIgnoreCase(rule.getConditionType())) {
                continue;
            }
            int threshold = parseInt(rule.getConditionValue(), 20);
            if (soc >= threshold) {
                continue;
            }
            if ("CREATE_CHARGE_TASK".equalsIgnoreCase(rule.getActionType())
                    || "CREATE_SWAP_TASK".equalsIgnoreCase(rule.getActionType())) {
                boolean swap = "CREATE_SWAP_TASK".equalsIgnoreCase(rule.getActionType())
                        || realFleetSwapCoordinator.prefersSwapRecovery(vehicle, parkId);
                String code = swap ? "AUTO_SWAP_REQUIRED" : "AUTO_CHARGE_REQUIRED";
                String action = swap ? "换电" : "回充";
                dispatchExceptionService.recordVehicleException(
                        vehicle.getId(),
                        code,
                        "规则「" + rule.getRuleName() + "」触发" + action + "：SOC " + soc + "% 低于阈值 " + threshold + "%");
                return true;
            }
        }
        return false;
    }

    @Override
    public void evaluateGeofenceBreach(Long parkId, VehicleEntity vehicle, String geofenceCode, String breachType) {
        if (vehicle == null || geofenceCode == null || breachType == null) {
            return;
        }
        for (DispatchAutomationRuleEntity rule : enabledRules(parkId)) {
            if (!geofenceCode.equalsIgnoreCase(rule.getConditionValue())) {
                continue;
            }
            String condition = rule.getConditionType() == null ? "" : rule.getConditionType().toUpperCase();
            if (!condition.equals(breachType) && !"GEOFENCE_BREACH".equals(condition)) {
                continue;
            }
            if ("CREATE_EXCEPTION".equalsIgnoreCase(rule.getActionType())) {
                dispatchExceptionService.recordVehicleException(
                        vehicle.getId(),
                        breachType,
                        "规则「" + rule.getRuleName() + "」响应围栏事件 " + geofenceCode);
            }
        }
    }

    private List<DispatchAutomationRuleEntity> enabledRules(Long parkId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<DispatchAutomationRuleEntity>()
                .eq(DispatchAutomationRuleEntity::getDeleted, 0)
                .eq(DispatchAutomationRuleEntity::getEnabled, 1)
                .eq(DispatchAutomationRuleEntity::getParkId, parkId == null ? 1L : parkId)
                .orderByAsc(DispatchAutomationRuleEntity::getId));
    }

    private double readDoubleParam(String json, String key, double fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has(key)) {
                return node.get(key).asDouble(fallback);
            }
        } catch (Exception ignored) {
            // use fallback
        }
        return fallback;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean isIdleLike(String stage) {
        return stage == null
                || "STANDBY".equals(stage)
                || "RETURNING_TO_STANDBY".equals(stage);
    }
}
