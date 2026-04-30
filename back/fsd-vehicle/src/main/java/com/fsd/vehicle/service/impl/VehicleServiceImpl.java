package com.fsd.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleMapper vehicleMapper;

    public VehicleServiceImpl(VehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    public VehicleEntity getById(Long vehicleId) {
        VehicleEntity vehicleEntity = vehicleMapper.selectById(vehicleId);
        if (vehicleEntity == null || Integer.valueOf(1).equals(vehicleEntity.getDeleted())) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found");
        }
        return vehicleEntity;
    }

    @Override
    public VehicleEntity getByVehicleCode(String vehicleCode) {
        VehicleEntity vehicleEntity = vehicleMapper.selectOne(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getVehicleCode, vehicleCode)
                .eq(VehicleEntity::getDeleted, 0)
                .last("limit 1"));
        if (vehicleEntity == null) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found");
        }
        return vehicleEntity;
    }

    @Override
    public List<VehicleEntity> listAssignableVehicles() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getOnlineStatus, VehicleOnlineStatus.ONLINE.name())
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.IDLE.name())
                .orderByDesc(VehicleEntity::getUpdatedAt));
    }

    @Override
    @Transactional
    public void occupyVehicle(Long vehicleId, Long taskId, Long orderId) {
        int updated = vehicleMapper.update(null, new LambdaUpdateWrapper<VehicleEntity>()
                .eq(VehicleEntity::getId, vehicleId)
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getOnlineStatus, VehicleOnlineStatus.ONLINE.name())
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.IDLE.name())
                .set(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.BUSY.name())
                .set(VehicleEntity::getCurrentTaskId, taskId)
                .set(VehicleEntity::getCurrentOrderId, orderId));
        if (updated != 1) {
            throw new BusinessException("VEHICLE_NOT_ASSIGNABLE", "Vehicle is not assignable");
        }
    }

    @Override
    @Transactional
    public void releaseVehicle(Long vehicleId, String nextDispatchStatus) {
        vehicleMapper.update(null, new LambdaUpdateWrapper<VehicleEntity>()
                .eq(VehicleEntity::getId, vehicleId)
                .eq(VehicleEntity::getDeleted, 0)
                .set(VehicleEntity::getDispatchStatus, nextDispatchStatus)
                .set(VehicleEntity::getCurrentTaskId, null)
                .set(VehicleEntity::getCurrentOrderId, null));
    }

    @Override
    @Transactional
    public VehicleEntity updateSnapshot(VehicleReportRequest request) {
        VehicleEntity vehicleEntity = getByVehicleCode(request.getVehicleCode());
        vehicleEntity.setOnlineStatus(request.getOnlineStatus());
        vehicleEntity.setDispatchStatus(request.getDispatchStatus());
        vehicleEntity.setCurrentLatitude(request.getLatitude());
        vehicleEntity.setCurrentLongitude(request.getLongitude());
        vehicleEntity.setBatteryLevel(request.getBatteryLevel());
        vehicleEntity.setLastReportTime(request.getReportTime());
        vehicleMapper.updateById(vehicleEntity);
        return vehicleEntity;
    }

    @Override
    public VehicleSummaryResponse getSummary() {
        long onlineCount = vehicleMapper.selectCount(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getOnlineStatus, VehicleOnlineStatus.ONLINE.name()));
        long idleCount = vehicleMapper.selectCount(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.IDLE.name()));
        long busyCount = vehicleMapper.selectCount(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.BUSY.name()));
        return VehicleSummaryResponse.builder()
                .onlineCount(onlineCount)
                .idleCount(idleCount)
                .busyCount(busyCount)
                .build();
    }
}
