package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.util.Optional;

/**
 * Vehicle ↔ parking slot / charging pile binding (P2-03, P2-SLOT).
 */
public interface ParkingFacilityService {

    Optional<ParkingSlotEntity> findSlotByVehicle(Long vehicleId);

    void releaseByVehicle(Long vehicleId);

    void markCharging(Long parkId, Long vehicleId, String slotCode);

    void occupyPluggedStandby(Long parkId, Long vehicleId, String slotCode);

    /**
     * Atomically reserve a free slot for a vehicle (P2-SLOT).
     *
     * @return true when reservation succeeds
     */
    boolean reserveSlot(Long parkId, Long vehicleId, String slotCode);

    /**
     * Clears RESERVED bindings for the vehicle without ending an active charging session on occupied slots.
     */
    void releaseReservation(Long vehicleId);

    /**
     * Reserve preferred charging slot, or the next free charging bay in the park.
     */
    Optional<ParkPointResponse> reserveChargingSlot(Long parkId, Long vehicleId, String preferredSlotCode);
}
