package com.fsd.dispatch.service;

import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.vehicle.entity.VehicleEntity;

public interface DispatchAutomationRuleService {

    /** Peak dispatch distance factor from BOOST_DISPATCH rules, or default when none match. */
    double resolvePeakDistanceFactor(Long parkId, double defaultFactor);

    /** Evaluate SOC / peak rules during simulation tick. */
    void evaluateSimulationTick(Long parkId, VehicleEntity vehicle, SimulationMotionState state);

    /** Evaluate energy rules for real fleet vehicles (creates charge exception when matched). */
    boolean evaluateFleetEnergyRules(Long parkId, VehicleEntity vehicle, String runtimeStage);
}
