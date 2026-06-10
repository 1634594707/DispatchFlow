package com.fsd.admin.service;

import com.fsd.admin.vo.AdminVehicleHealthResponse;

public interface VehicleHealthAdminService {

    AdminVehicleHealthResponse getHealth(Long vehicleId);
}
