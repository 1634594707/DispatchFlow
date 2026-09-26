package com.fsd.vehicle.service;

import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import java.util.List;

public interface VehicleService {

    VehicleEntity getById(Long vehicleId);

    VehicleEntity getByVehicleCode(String vehicleCode);

    List<VehicleEntity> listAssignableVehicles();

    void occupyVehicle(Long vehicleId, Long taskId, Long orderId);

    /**
     * 抢车的"预期内失败"版本：抢不到返回 false，**不抛异常**。
     *
     * <p>为什么要有这条：{@link #occupyVehicle} 抛的 {@code VEHICLE_NOT_ASSIGNABLE} 在自动派单里
     * 是两台并发订单抢同一台车的正常结果，但它跨过了 {@code @Transactional} 边界 —— Spring 会把
     * 调用方的**共享事务**标成 rollback-only，调用方 catch 掉继续写，最后 commit 时抛
     * {@code UnexpectedRollbackException}：客户端收到 500，刚创建的订单与任务一起回滚。
     * 压测实测冷车队下这条占下单量的 6.45%（车队饱和时反而为 0，因为根本走不到抢车那一步）。
     * 所以"抢不到"必须是返回值，不能是异常。人工指定车辆的调用方仍用会抛的那个：那里失败要看得见。
     */
    boolean tryOccupyVehicle(Long vehicleId, Long taskId, Long orderId);

    void releaseVehicle(Long vehicleId, String nextDispatchStatus);

    /**
     * 将车辆 dispatchStatus 置为 UNAVAILABLE（紧急停车）。
     * 用于围栏 BLOCK 级别响应或其他安全场景。仅更新状态，不清理 currentTaskId/currentOrderId
     * （由后续任务取消流程负责清理），避免掩盖进行中的任务上下文。
     */
    void markUnavailable(Long vehicleId);

    VehicleEntity updateSnapshot(VehicleReportRequest request);

    VehicleSummaryResponse getSummary();

    VehicleSummaryResponse getSummary(Long parkId);
}
