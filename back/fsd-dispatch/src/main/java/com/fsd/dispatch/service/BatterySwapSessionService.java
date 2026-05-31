package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.BatterySwapSessionEntity;
import java.util.Optional;

public interface BatterySwapSessionService {

    BatterySwapSessionEntity startSession(Long parkId, Long vehicleId, Long cabinetId, int startSoc);

    void completeActiveSession(Long vehicleId);

    Optional<BatterySwapSessionEntity> findActiveByVehicle(Long vehicleId);
}
