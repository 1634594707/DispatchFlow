package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.util.List;
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

    /**
     * 空闲车"回待命区"占一个 {@code STANDBY} 车位（幂等：已经占着 STANDBY 位就续用同一个）。
     *
     * <p>为什么要有这一条：仿真器原来把待命点回退到 {@code application.yml} 里那组老示意图像素坐标，
     * 于是车被摆到画布上凭空生成的点上（实测 3 台因此落到服务围栏外、且叠在同一个坐标）。
     * 待命位的真身在 {@code t_parking_slot}，它带一致的像素 + GCJ-02 坐标与进/出节点。
     *
     * @return 占到位则返回该车位坐标；园区没有空闲 STANDBY 位时返回空，由调用方决定兜底
     */
    Optional<ParkPointResponse> reserveStandbySlot(Long parkId, Long vehicleId);

    /**
     * 园区内全部 {@code STANDBY} 车位（按 {@code sort_order} 升序），不做占用。
     *
     * <p>给"车还不存在"的那一刻用：`ensurePilotFleet` 首次铺仿真车队时车辆行尚未插入，
     * 没有 vehicleId 可占位，只能按序号轮转分配初始位置。
     */
    List<ParkPointResponse> listStandbySlots(Long parkId);
}
