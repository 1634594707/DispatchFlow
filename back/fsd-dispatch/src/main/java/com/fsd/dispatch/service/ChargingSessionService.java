package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.ChargingSessionEntity;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionService {

    ChargingSessionEntity startSession(Long parkId, Long vehicleId, Long parkingSlotId, Long chargingPileId, int startSoc);

    void completeActiveSession(Long vehicleId, int endSoc);

    Optional<ChargingSessionEntity> findActiveByVehicle(Long vehicleId);

    /**
     * 预测未来一段时间内的充电需求
     * @param parkId 园区ID
     * @param lookaheadMinutes 预测时间窗口（分钟）
     * @return 预计需要充电的车辆数
     */
    int predictChargingDemand(Long parkId, int lookaheadMinutes);

    /**
     * 智能分配充电桩
     * @param vehicleId 车辆ID
     * @return 推荐的充电桩ID，null表示无可用桩
     */
    Long recommendChargingPile(Long vehicleId);

    /**
     * 获取充电排队队列（按优先级排序）
     * @param parkId 园区ID
     * @return 按优先级排序的待充电车辆列表
     */
    List<Long> getChargingQueue(Long parkId);
}
