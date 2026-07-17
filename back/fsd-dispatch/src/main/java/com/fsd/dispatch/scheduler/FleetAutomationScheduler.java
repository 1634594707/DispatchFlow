package com.fsd.dispatch.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FleetAutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(FleetAutomationScheduler.class);

    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final FleetChargePolicy fleetChargePolicy;
    private final DispatchAutomationRuleService automationRuleService;
    private final ChargingSessionService chargingSessionService;

    @Value("${fsd.automation.default-park-id:1}")
    private long defaultParkId;

    public FleetAutomationScheduler(VehicleMapper vehicleMapper,
                                    FleetRuntimeService fleetRuntimeService,
                                    FleetChargePolicy fleetChargePolicy,
                                    DispatchAutomationRuleService automationRuleService,
                                    ChargingSessionService chargingSessionService) {
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetChargePolicy = fleetChargePolicy;
        this.automationRuleService = automationRuleService;
        this.chargingSessionService = chargingSessionService;
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

    /**
     * ALG-10 fix: periodically scan for charging sessions that have exceeded the
     * configured timeout and release the stuck vehicles. Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${fsd.automation.charging-timeout-check-ms:300000}")
    public void timeoutStaleChargingSessions() {
        try {
            int timedOut = chargingSessionService.timeoutStaleChargingSessions();
            if (timedOut > 0) {
                log.warn("ALG-10: timed out {} stale charging session(s); vehicles released to IDLE", timedOut);
            }
        } catch (Exception ex) {
            log.warn("ALG-10: charging-timeout sweep failed: {}", ex.getMessage());
        }
    }
}
