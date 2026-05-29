package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.ChargingSessionEntity;
import java.util.Optional;

public interface ChargingSessionService {

    ChargingSessionEntity startSession(Long parkId, Long vehicleId, Long parkingSlotId, Long chargingPileId, int startSoc);

    void completeActiveSession(Long vehicleId, int endSoc);

    Optional<ChargingSessionEntity> findActiveByVehicle(Long vehicleId);
}
