package com.fsd.vehicle.service;

import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.util.List;

public interface VehicleAdminQueryService {

    List<VehicleAdminListItemResponse> listVehicles();

    default List<VehicleAdminListItemResponse> listVehicles(Long parkId) {
        return listVehicles().stream()
                .filter(vehicle -> parkId == null || parkId.equals(vehicle.getParkId()))
                .toList();
    }

    VehicleAdminDetailResponse getVehicleDetail(Long vehicleId);
}
