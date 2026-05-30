package com.fsd.dispatch.service;

import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;

public interface DispatchStrategyRuntimeService {

    DispatchScoringProperties scoringForAssign(Long parkId);

    FleetEnergyProperties energyForAssign(Long parkId);

    void refreshCache();
}
