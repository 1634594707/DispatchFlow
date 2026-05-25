package com.fsd.dispatch.fleet.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FleetChargePolicyImplTest {

    private FleetChargePolicyImpl fleetChargePolicy;

    @BeforeEach
    void setUp() {
        FleetEnergyProperties properties = new FleetEnergyProperties();
        properties.setLowSocThreshold(25);
        properties.setMinAssignableSoc(30);
        properties.setFullSoc(100);
        properties.setPluggedStandbyNoDrain(true);
        fleetChargePolicy = new FleetChargePolicyImpl(properties);
    }

    @Test
    void pluggedInFullStandbyShouldSkipDrain() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setBatteryLevel(100);
        FleetRuntime runtime = FleetRuntime.builder()
                .runtimeStage("STANDBY")
                .pluggedIn(true)
                .build();

        assertTrue(fleetChargePolicy.shouldSkipDrain(vehicle, runtime));
        assertTrue(fleetChargePolicy.isAssignable(vehicle));
        assertFalse(fleetChargePolicy.isActivelyCharging("STANDBY"));
    }

    @Test
    void lowSocVehicleShouldNotBeAssignable() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setBatteryLevel(20);

        assertTrue(fleetChargePolicy.isLowSoc(20));
        assertFalse(fleetChargePolicy.isAssignable(vehicle));
    }

    @Test
    void chargingStageShouldBeDetected() {
        assertTrue(fleetChargePolicy.isActivelyCharging("CHARGING"));
        assertTrue(fleetChargePolicy.isActivelyCharging("TO_CHARGING"));
    }
}
