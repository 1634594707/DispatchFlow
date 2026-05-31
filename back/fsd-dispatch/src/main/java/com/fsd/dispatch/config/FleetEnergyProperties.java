package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.fleet.energy")
public class FleetEnergyProperties {

    private int lowSocThreshold = 25;

    private int minAssignableSoc = 30;

    private int fullSoc = 100;

    private int chargeRatePerTick = 4;

    private int reserveSocFloor = 8;

    private int busyDrainIntervalTicks = 4;

    private double idleDrainProbability = 0.06D;

    private boolean pluggedStandbyNoDrain = true;

    private boolean idleChargeWhenNoDemand = true;

    /** CHARGE, SWAP, or AUTO */
    private String energyRecoveryMode = "CHARGE";

    private int swapDurationTicks = 5;
}
