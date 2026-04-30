package com.fsd.vehicle.service;

import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.util.List;

public interface VehicleAdminQueryService {

    List<VehicleAdminListItemResponse> listVehicles();

    VehicleAdminDetailResponse getVehicleDetail(Long vehicleId);
}
