package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;

public interface FleetChargePolicy {

    boolean isLowSoc(Integer batteryLevel);

    boolean isFullyCharged(Integer batteryLevel);

    boolean isAssignable(VehicleEntity vehicle);

    boolean shouldSkipDrain(VehicleEntity vehicle, FleetRuntime runtime);

    boolean isActivelyCharging(String runtimeStage);
}
