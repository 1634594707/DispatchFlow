package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;
import org.springframework.stereotype.Component;

@Component
public class FleetChargePolicyImpl implements FleetChargePolicy {

    private final FleetEnergyProperties fleetEnergyProperties;
    /**
     * 阶段八 8.4：充电触发阈值热更新解析器。
     * 4 个阈值（returnToCharge / minAssignableSoc / chargeCompleteSoc / criticalSoc）
     * 支持 Redis 动态配置，修改后 5s 内生效，无需重启。
     */
    private final FleetEnergyThresholdResolver thresholdResolver;

    public FleetChargePolicyImpl(FleetEnergyProperties fleetEnergyProperties,
                                 FleetEnergyThresholdResolver thresholdResolver) {
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.thresholdResolver = thresholdResolver;
    }

    @Override
    public boolean isLowSoc(Integer batteryLevel) {
        return normalizeSoc(batteryLevel) <= fleetEnergyProperties.getLowSocThreshold();
    }

    @Override
    public boolean shouldReturnToCharge(Integer batteryLevel) {
        // 阶段八 8.4：使用 Redis 热更新阈值
        return normalizeSoc(batteryLevel) <= thresholdResolver.getReturnToChargeThreshold();
    }

    @Override
    public boolean isCriticalSoc(Integer batteryLevel) {
        // 阶段八 8.4：使用 Redis 热更新阈值
        return normalizeSoc(batteryLevel) <= thresholdResolver.getCriticalSocThreshold();
    }

    @Override
    public boolean isFullyCharged(Integer batteryLevel) {
        return normalizeSoc(batteryLevel) >= fleetEnergyProperties.getFullSoc();
    }

    @Override
    public boolean isChargeSessionComplete(Integer batteryLevel) {
        // 阶段八 8.4：使用 Redis 热更新阈值
        return normalizeSoc(batteryLevel) >= thresholdResolver.getChargeCompleteSoc();
    }

    @Override
    public boolean isAssignable(VehicleEntity vehicle) {
        // 阶段八 8.4：使用 Redis 热更新阈值
        return normalizeSoc(vehicle.getBatteryLevel()) >= thresholdResolver.getMinAssignableSoc();
    }

    @Override
    public boolean shouldSkipDrain(VehicleEntity vehicle, FleetRuntime runtime) {
        if (isActivelyCharging(runtime.getRuntimeStage())) {
            return true;
        }
        if (!fleetEnergyProperties.isPluggedStandbyNoDrain()) {
            return false;
        }
        return Boolean.TRUE.equals(runtime.getPluggedIn())
                && isFullyCharged(vehicle.getBatteryLevel())
                && "STANDBY".equals(runtime.getRuntimeStage());
    }

    @Override
    public boolean isActivelyCharging(String runtimeStage) {
        return "TO_CHARGING".equals(runtimeStage) || "CHARGING".equals(runtimeStage);
    }

    @Override
    public boolean isActivelySwapping(String runtimeStage) {
        return "TO_SWAP".equals(runtimeStage) || "SWAPPING".equals(runtimeStage);
    }

    private int normalizeSoc(Integer batteryLevel) {
        return batteryLevel == null ? fleetEnergyProperties.getFullSoc() : batteryLevel;
    }
}
