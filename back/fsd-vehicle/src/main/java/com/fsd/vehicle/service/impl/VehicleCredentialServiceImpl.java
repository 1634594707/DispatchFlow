package com.fsd.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.exception.BusinessException;
import com.fsd.vehicle.entity.VehicleCredentialEntity;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleCredentialMapper;
import com.fsd.vehicle.service.VehicleCredentialService;
import com.fsd.vehicle.service.VehicleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VehicleCredentialServiceImpl implements VehicleCredentialService {

    private final VehicleCredentialMapper vehicleCredentialMapper;
    private final VehicleService vehicleService;

    public VehicleCredentialServiceImpl(VehicleCredentialMapper vehicleCredentialMapper,
                                        VehicleService vehicleService) {
        this.vehicleCredentialMapper = vehicleCredentialMapper;
        this.vehicleService = vehicleService;
    }

    @Override
    public VehicleEntity authenticate(String vehicleCode, String apiKey) {
        if (vehicleCode == null || vehicleCode.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("VEHICLE_AUTH_REQUIRED", "Vehicle code and API key are required");
        }
        VehicleEntity vehicle = vehicleService.getByVehicleCode(vehicleCode);
        Page<VehicleCredentialEntity> credentialPage = vehicleCredentialMapper.selectPage(new Page<>(1, 1, false), new LambdaQueryWrapper<VehicleCredentialEntity>()
                .eq(VehicleCredentialEntity::getVehicleId, vehicle.getId())
                .eq(VehicleCredentialEntity::getApiKey, apiKey)
                .eq(VehicleCredentialEntity::getStatus, "ACTIVE"));
        List<VehicleCredentialEntity> credentialRecords = credentialPage.getRecords();
        VehicleCredentialEntity credential = credentialRecords.isEmpty() ? null : credentialRecords.get(0);
        if (credential == null) {
            throw new BusinessException("VEHICLE_AUTH_FAILED", "Invalid vehicle credentials");
        }
        return vehicle;
    }
}
