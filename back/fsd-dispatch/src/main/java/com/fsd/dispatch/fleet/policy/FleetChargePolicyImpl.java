package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;
import org.springframework.stereotype.Component;

@Component
public class FleetChargePolicyImpl implements FleetChargePolicy {

    private final FleetEnergyProperties fleetEnergyProperties;

    public FleetChargePolicyImpl(FleetEnergyProperties fleetEnergyProperties) {
        this.fleetEnergyProperties = fleetEnergyProperties;
    }

    @Override
    public boolean isLowSoc(Integer batteryLevel) {
        return normalizeSoc(batteryLevel) <= fleetEnergyProperties.getLowSocThreshold();
    }

    @Override
    public boolean isFullyCharged(Integer batteryLevel) {
        return normalizeSoc(batteryLevel) >= fleetEnergyProperties.getFullSoc();
    }

    @Override
    public boolean isAssignable(VehicleEntity vehicle) {
        return normalizeSoc(vehicle.getBatteryLevel()) >= fleetEnergyProperties.getMinAssignableSoc();
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
