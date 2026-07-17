package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.fleet.energy")
public class FleetEnergyProperties {

    /** 地图低电量展示阈值（%） */
    private int lowSocThreshold = 20;

    /**
     * 自动回充触发阈值（%）。统一阈值：SOC 低于 20% 的 IDLE 车辆自动进入充电队列。
     * 历史值 15% 偏低，导致车辆在临界状态下仍被派单，增加抛锚风险。
     */
    private int returnToChargeThreshold = 20;

    /** 危急电量驻车阈值（%） */
    private int criticalSocThreshold = 5;

    /**
     * 最低可派单 SOC（%）。统一阈值：SOC 低于 30% 的车辆不再派新任务。
     * 历史值 25% 与 returnToChargeThreshold(15%) 之间只有 10% 余量，
     * 在长路径任务中容易耗尽电量；提升至 30% 给 10% 安全垫。
     */
    private int minAssignableSoc = 30;

    private int fullSoc = 100;

    /** 充电结束并恢复派单阈值（%） */
    private int chargeCompleteSoc = 90;

    private int chargeRatePerTick = 4;

    private int reserveSocFloor = 5;

    private int busyDrainIntervalTicks = 8;

    /** 真实地图配送：每下降 1% SOC 约需行驶的米数（按 geo 弧长计）。 */
    private double busyDrainMetersPerPercent = 150D;

    private double idleDrainProbability = 0.06D;

    private boolean pluggedStandbyNoDrain = true;

    private boolean idleChargeWhenNoDemand = true;

    /** CHARGE, SWAP, or AUTO */
    private String energyRecoveryMode = "CHARGE";

    private int swapDurationTicks = 5;

    /**
     * ALG-10 fix: maximum duration (minutes) a charging session may remain ACTIVE before
     * the timeout scheduler forcibly terminates it. A faulty charging pile that never
     * reports full SOC would otherwise leave the vehicle permanently stuck in CHARGING.
     * Set to 0 to disable the timeout (not recommended for production).
     */
    private int chargingTimeoutMinutes = 240;
}
