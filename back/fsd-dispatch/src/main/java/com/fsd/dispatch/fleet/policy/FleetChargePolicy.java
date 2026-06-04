package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;

public interface FleetChargePolicy {

    boolean isLowSoc(Integer batteryLevel);

    boolean shouldReturnToCharge(Integer batteryLevel);

    boolean isCriticalSoc(Integer batteryLevel);

    boolean isFullyCharged(Integer batteryLevel);

    /** 充电会话是否可结束（驶离充电站） */
    boolean isChargeSessionComplete(Integer batteryLevel);

    boolean isAssignable(VehicleEntity vehicle);

    boolean shouldSkipDrain(VehicleEntity vehicle, FleetRuntime runtime);

    boolean isActivelyCharging(String runtimeStage);

    boolean isActivelySwapping(String runtimeStage);
}
