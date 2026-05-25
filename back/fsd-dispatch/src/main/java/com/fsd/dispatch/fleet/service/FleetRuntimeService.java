package com.fsd.dispatch.fleet.service;

import com.fsd.dispatch.fleet.model.FleetRuntime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface FleetRuntimeService {

    Optional<FleetRuntime> get(Long vehicleId);

    FleetRuntime getOrCreate(Long vehicleId, Supplier<FleetRuntime> supplier);

    void save(FleetRuntime runtime);

    Map<Long, FleetRuntime> getBatch(Collection<Long> vehicleIds);
}
