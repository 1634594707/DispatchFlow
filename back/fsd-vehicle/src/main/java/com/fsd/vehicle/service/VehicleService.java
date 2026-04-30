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

    VehicleEntity updateSnapshot(VehicleReportRequest request);

    VehicleSummaryResponse getSummary();
}
