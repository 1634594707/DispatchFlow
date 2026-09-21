package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.entity.DispatchAutomationRuleEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.mapper.DispatchAutomationRuleMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.fleet.real.RealFleetSwapCoordinator;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.EnergyForecastService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DispatchAutomationRuleServiceImpl implements DispatchAutomationRuleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchAutomationRuleServiceImpl.class);

    private static final List<String> DISPATCH_BACKLOG_STATUSES = List.of(
            DispatchTaskStatus.PENDING.name(),
            DispatchTaskStatus.MANUAL_PENDING.name(),
            DispatchTaskStatus.ASSIGNING.name());

    private final DispatchAutomationRuleMapper ruleMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final PeakModeService peakModeService;
    private final DispatchExceptionService dispatchExceptionService;
    private final RealFleetSwapCoordinator realFleetSwapCoordinator;
    private final EnergyForecastService energyForecastService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DispatchAutomationRuleServiceImpl(DispatchAutomationRuleMapper ruleMapper,
                                               DispatchTaskMapper dispatchTaskMapper,
                                               PeakModeService peakModeService,
                                               DispatchExceptionService dispatchExceptionService,
                                               RealFleetSwapCoordinator realFleetSwapCoordinator,
                                               EnergyForecastService energyForecastService) {
        this.ruleMapper = ruleMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.peakModeService = peakModeService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.realFleetSwapCoordinator = realFleetSwapCoordinator;
        this.energyForecastService = energyForecastService;
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
                    // 有待派任务时优先恢复可派车，避免全员卡在 WAIT_CHARGING 导致 NO_VEHICLE/LOW_SOC 雪崩
                    if (hasDispatchBacklog(parkId)) {
                        continue;
                    }
                    state.stage = "WAIT_CHARGING";
                }
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
                if (!swap && deferChargeReturn(parkId, vehicle, soc, rule)) {
                    continue;
                }
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

    /**
     * 错峰返充（§13.14③ 的数字支持）：园区预测压力高于阈值时，先不把这辆低电车送去充电，
     * 把运力留在派单上。安全兜底在 {@link EnergyForecastService#shouldDeferReturnToCharge} 内部 ——
     * SOC 已接近临界时永不推迟；没有预测数据时该方法返回 false，等价于接线前的纯阈值行为。
     *
     * <p>换电不走这条路：换电站不等排队，推迟没有收益。
     */
    private boolean deferChargeReturn(Long parkId, VehicleEntity vehicle, int soc, DispatchAutomationRuleEntity rule) {
        if (!energyForecastService.shouldDeferReturnToCharge(LocalDate.now(), parkId, soc)) {
            return false;
        }
        LOGGER.info("错峰推迟返充：vehicleId={} soc={} 规则「{}」压力={} 未创建补能任务，本轮跳过",
                vehicle.getId(), soc, rule.getRuleName(),
                energyForecastService.parkPressure(LocalDate.now(), parkId));
        return true;
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
            String condition = rule.getConditionType() == null ? "" : rule.getConditionType().toUpperCase(Locale.ROOT);
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
                .eq(DispatchAutomationRuleEntity::getParkId, parkId != null ? parkId.longValue() : 1L)
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
        } catch (java.io.IOException ignored) {
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

    private boolean hasDispatchBacklog(Long parkId) {
        Long count = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .in(DispatchTaskEntity::getStatus, DISPATCH_BACKLOG_STATUSES));
        return count != null && count > 0;
    }

    private boolean isIdleLike(String stage) {
        return stage == null
                || "STANDBY".equals(stage)
                || "RETURNING_TO_STANDBY".equals(stage);
    }
}
