package com.fsd.dispatch.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FleetAutomationScheduler {

    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final FleetChargePolicy fleetChargePolicy;
    private final DispatchAutomationRuleService automationRuleService;

    @Value("${fsd.automation.default-park-id:1}")
    private long defaultParkId;

    public FleetAutomationScheduler(VehicleMapper vehicleMapper,
                                    FleetRuntimeService fleetRuntimeService,
                                    FleetChargePolicy fleetChargePolicy,
                                    DispatchAutomationRuleService automationRuleService) {
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetChargePolicy = fleetChargePolicy;
        this.automationRuleService = automationRuleService;
    }

    @Scheduled(fixedDelayString = "${fsd.automation.fleet-check-ms:120000}")
    public void evaluateRealFleetRules() {
        List<VehicleEntity> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .in(VehicleEntity::getLinkMode,
                        VehicleLinkMode.REAL.name(),
                        VehicleLinkMode.VDA5050.name()));
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.getCurrentTaskId() != null) {
                continue;
            }
            FleetRuntime runtime = fleetRuntimeService.get(vehicle.getId()).orElse(null);
            String stage = runtime != null ? runtime.getRuntimeStage() : null;
            if (fleetChargePolicy.isActivelyCharging(stage) || fleetChargePolicy.isActivelySwapping(stage)) {
                continue;
            }
            automationRuleService.evaluateFleetEnergyRules(defaultParkId, vehicle, stage);
        }
    }
}
