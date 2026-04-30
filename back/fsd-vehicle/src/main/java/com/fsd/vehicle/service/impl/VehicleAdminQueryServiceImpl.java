package com.fsd.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VehicleAdminQueryServiceImpl implements VehicleAdminQueryService {

    private final VehicleMapper vehicleMapper;
    private final VehicleService vehicleService;

    public VehicleAdminQueryServiceImpl(VehicleMapper vehicleMapper, VehicleService vehicleService) {
        this.vehicleMapper = vehicleMapper;
        this.vehicleService = vehicleService;
    }

    @Override
    public List<VehicleAdminListItemResponse> listVehicles() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .orderByDesc(VehicleEntity::getUpdatedAt))
                .stream()
                .map(vehicle -> VehicleAdminListItemResponse.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleCode(vehicle.getVehicleCode())
                        .vehicleName(vehicle.getVehicleName())
                        .onlineStatus(vehicle.getOnlineStatus())
                        .dispatchStatus(vehicle.getDispatchStatus())
                        .currentTaskId(vehicle.getCurrentTaskId())
                        .currentOrderId(vehicle.getCurrentOrderId())
                        .batteryLevel(vehicle.getBatteryLevel())
                        .lastReportTime(vehicle.getLastReportTime())
                        .build())
                .toList();
    }

    @Override
    public VehicleAdminDetailResponse getVehicleDetail(Long vehicleId) {
        VehicleEntity vehicle = vehicleService.getById(vehicleId);
        return VehicleAdminDetailResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleCode(vehicle.getVehicleCode())
                .vehicleName(vehicle.getVehicleName())
                .vehicleType(vehicle.getVehicleType())
                .onlineStatus(vehicle.getOnlineStatus())
                .dispatchStatus(vehicle.getDispatchStatus())
                .currentTaskId(vehicle.getCurrentTaskId())
                .currentOrderId(vehicle.getCurrentOrderId())
                .currentLatitude(vehicle.getCurrentLatitude())
                .currentLongitude(vehicle.getCurrentLongitude())
                .batteryLevel(vehicle.getBatteryLevel())
                .lastReportTime(vehicle.getLastReportTime())
                .remark(vehicle.getRemark())
                .build();
    }
}
