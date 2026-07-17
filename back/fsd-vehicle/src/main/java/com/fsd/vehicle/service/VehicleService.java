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

    void releaseVehicle(Long vehicleId, String nextDispatchStatus);

    /**
     * 将车辆 dispatchStatus 置为 UNAVAILABLE（紧急停车）。
     * 用于围栏 BLOCK 级别响应或其他安全场景。仅更新状态，不清理 currentTaskId/currentOrderId
     * （由后续任务取消流程负责清理），避免掩盖进行中的任务上下文。
     */
    void markUnavailable(Long vehicleId);

    VehicleEntity updateSnapshot(VehicleReportRequest request);

    VehicleSummaryResponse getSummary();
}
