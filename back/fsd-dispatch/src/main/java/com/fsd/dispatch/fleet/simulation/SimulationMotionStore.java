package com.fsd.dispatch.fleet.simulation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SimulationMotionStore {

    private final Map<Long, SimulationMotionState> states = new ConcurrentHashMap<>();

    public SimulationMotionState getOrCreate(Long vehicleId, Supplier<SimulationMotionState> supplier) {
        return states.computeIfAbsent(vehicleId, key -> supplier.get());
    }

    public SimulationMotionState get(Long vehicleId) {
        return states.get(vehicleId);
    }

    public void put(Long vehicleId, SimulationMotionState state) {
        states.put(vehicleId, state);
    }
}
