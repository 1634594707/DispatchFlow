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

    /** 自动回充触发阈值（%） */
    private int returnToChargeThreshold = 15;

    /** 危急电量驻车阈值（%） */
    private int criticalSocThreshold = 5;

    private int minAssignableSoc = 25;

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
}
