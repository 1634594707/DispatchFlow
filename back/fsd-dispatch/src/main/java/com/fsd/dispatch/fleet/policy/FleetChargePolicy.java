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

    /**
     * P1-2：补能阈值统一出口。所有补能入口（返充预测、错峰延迟、压力恢复、可派判定）
     * 都从这里拿阈值——背后是 {@link FleetEnergyThresholdResolver} 的 Redis 热更新 + YAML 回退，
     * 不允许各处直读 {@code FleetEnergyProperties}（那是热更新覆盖不到的旁路）。
     */
    int returnToChargeThreshold();

    int criticalSocThreshold();

    int minAssignableSoc();

    int lowSocThreshold();

    int chargeCompleteSoc();
}
