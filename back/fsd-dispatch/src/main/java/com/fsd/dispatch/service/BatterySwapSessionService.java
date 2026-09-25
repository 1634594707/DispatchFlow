package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.BatterySwapSessionEntity;
import java.util.Optional;

public interface BatterySwapSessionService {

    BatterySwapSessionEntity startSession(Long parkId, Long vehicleId, Long cabinetId, int startSoc);

    void completeActiveSession(Long vehicleId);

    Optional<BatterySwapSessionEntity> findActiveByVehicle(Long vehicleId);

    /**
     * 这台柜**当前**正在换的车数（状态 {@code IN_PROGRESS}）。
     *
     * <p>换电容量是"按点位算"的：一台柜的 {@code slot_count} 就是它能同时服务几台车，
     * 所以选柜之前必须先问这个数 —— 不然全部车队会被导到最近的那一台上排队。
     */
    long countActiveAtCabinet(Long cabinetId);
}
