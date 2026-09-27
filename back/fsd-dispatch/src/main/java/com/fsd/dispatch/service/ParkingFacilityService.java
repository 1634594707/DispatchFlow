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
     * 只释放这台车**预留中的 STANDBY 位**（不动充电位、不动桩位、不结束任何会话）。
     *
     * <p>为什么要有这一条而不用 {@link #releaseByVehicle(Long)}：去充电的车应该把待命位让给别的车，
     * 但它此刻已经抢到的**桩位**不能被一起解绑 —— 用整量释放就会把自己刚 RESERVED 的桩位放回 FREE，
     * 下一辆车立刻占走，等这台车开到桩前 `markCharging` 必然抛
     * `PARKING_SLOT_CONFLICT`（生产实测 6 分钟 13 次，仿真定时任务被中断）。
     *
     * <p>判据只能是**车位编码**，不能是 `slot_type`：母港那 6 根桩本来就绑在 STANDBY 型车位
     * （P1..P6）上，按类型筛会把刚抢到的桩位一起放掉。
     */
    void releaseSlotReservation(Long vehicleId, String slotCode);

    /**
     * 释放"没有任何依据"的补能位占用，返回释放条数。
     *
     * <p>为什么需要：车位绑定与充电会话是两套状态，而只有会话侧有超时回收（ALG-10）。补能完成路径
     * 只要有一次没走到释放（重启丢掉仿真内存态、#17 那条 {@code releaseByVehicle} 死锁回滚、
     * {@code reserveStandbySlot} 曾给同一台车发第二个位），桩位行就永远停在 RESERVED/OCCUPIED，
     * 而 {@code reserveSlot} 要求 {@code status=FREE 且 occupied_vehicle_id IS NULL} ⇒ 谁也抢不到，
     * 包括那台名义上还挂着它的车。生产实测：22 个桩位全占、ACTIVE 会话 0、35 台车 8–30% 电量、
     * 22 小时没充进一度电。
     *
     * <p>判据是**两条同时成立**，缺一都不放：① 这台车没有 ACTIVE 充电会话；② 这台车此刻不在该位坐标上。
     * 只查①会误放"插在桩上待命"的车（那是设计状态），只查②会误放正在充电但遥测迟到的车。
     */
    int releaseOrphanEnergySlots();

    /**
     * Reserve preferred charging slot, or the next free charging bay in the park.
     */
    Optional<ParkPointResponse> reserveChargingSlot(Long parkId, Long vehicleId, String preferredSlotCode);

    /**
     * P1-2：带起点坐标的选桩重载。候选按"预计完成时间"升序尝试——
     * 行驶时间（有起点才算，像素坐标按启发式速度折算）+ 按桩 {@code max_power_kw}
     * 折算的充电时长；忙桩不进候选（抢不到位 = 谈不了排队），
     * 所以真实的权衡发生在"近而慢的桩 vs 远而快的桩"。
     * {@code preferredSlotCode} 仍是首位（粘滞）：已经开去 A 桩的车不因排序被半路改派。
     */
    Optional<ParkPointResponse> reserveChargingSlot(Long parkId, Long vehicleId, String preferredSlotCode,
                                                    Double fromX, Double fromY);

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

    /**
     * 园区内**全部**车位（待命位 + 桩位），带状态与占用者车号，给地图画车位层用。
     *
     * <p>为什么不能复用 {@link #listStandbySlots(Long)}：那条只给 STANDBY 型、且不带占用信息，
     * 而"车为什么不停到位上"这个问题恰恰需要看见桩位与被谁占着。也不给 {@code FREE} 位过滤 ——
     * 空位也要画，否则看不出"位在这儿、车没回来"。
     */
    List<ParkPointResponse> listSlotMarkers(Long parkId);
}
