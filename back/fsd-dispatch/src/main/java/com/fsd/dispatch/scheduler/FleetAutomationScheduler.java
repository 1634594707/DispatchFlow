package com.fsd.dispatch.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.ParkingFacilityService;
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
    private final ParkingFacilityService parkingFacilityService;

    @Value("${fsd.automation.default-park-id:1}")
    private long defaultParkId;

    public FleetAutomationScheduler(VehicleMapper vehicleMapper,
                                    FleetRuntimeService fleetRuntimeService,
                                    FleetChargePolicy fleetChargePolicy,
                                    DispatchAutomationRuleService automationRuleService,
                                    ChargingSessionService chargingSessionService,
                                    ParkingFacilityService parkingFacilityService) {
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetChargePolicy = fleetChargePolicy;
        this.automationRuleService = automationRuleService;
        this.chargingSessionService = chargingSessionService;
        this.parkingFacilityService = parkingFacilityService;
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

    /**
     * 补能位对账：把"没有 ACTIVE 会话、车也不在该位"的桩位占用放掉（判据见
     * {@link ParkingFacilityService#releaseOrphanEnergySlots()}）。
     *
     * <p>ALG-10 只按**会话**找茬，而泄漏发生在**车位**那一侧（重启丢仿真内存态、
     * {@code releaseByVehicle} 死锁回滚、曾给同一台车发第二个位），所以会话全空的时候它一条都不会动 ——
     * 生产实测正是这一状态：ACTIVE 会话 0、22 个桩位全 OCCUPIED、35 台车 8–30% 电量、22 小时充不进电。
     */
    @Scheduled(fixedDelayString = "${fsd.automation.slot-reconcile-ms:300000}")
    public void reconcileOrphanEnergySlots() {
        try {
            int released = parkingFacilityService.releaseOrphanEnergySlots();
            if (released > 0) {
                log.warn("SLOT-RECON: released {} orphan energy slot(s) with no active session and no vehicle in place", released);
            }
        } catch (Exception ex) {
            log.warn("SLOT-RECON: slot reconciliation failed: {}", ex.getMessage());
        }
    }
}
