package com.fsd.vehicle.service;

import com.fsd.vehicle.entity.VehicleEntity;

public interface VehicleCredentialService {

    VehicleEntity authenticate(String vehicleCode, String apiKey);
}
