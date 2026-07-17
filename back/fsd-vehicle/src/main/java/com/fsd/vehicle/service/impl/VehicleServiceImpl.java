package com.fsd.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.geo.Wgs84Gcj02Converter;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import java.math.BigDecimal;
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
        Page<VehicleEntity> page = vehicleMapper.selectPage(new Page<>(1, 1, false), new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getVehicleCode, vehicleCode)
                .eq(VehicleEntity::getDeleted, 0));
        List<VehicleEntity> records = page.getRecords();
        VehicleEntity vehicleEntity = records.isEmpty() ? null : records.get(0);
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
                .set(VehicleEntity::getCurrentOrderId, null)
                .set(VehicleEntity::getCurrentLoad, 0));
    }

    @Override
    @Transactional
    public void markUnavailable(Long vehicleId) {
        vehicleMapper.update(null, new LambdaUpdateWrapper<VehicleEntity>()
                .eq(VehicleEntity::getId, vehicleId)
                .eq(VehicleEntity::getDeleted, 0)
                .set(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.UNAVAILABLE.name()));
    }

    @Override
    @Transactional
    public VehicleEntity updateSnapshot(VehicleReportRequest request) {
        VehicleEntity vehicleEntity = getByVehicleCode(request.getVehicleCode());
        vehicleEntity.setOnlineStatus(request.getOnlineStatus());
        vehicleEntity.setDispatchStatus(request.getDispatchStatus());
        // Phase 5 任务 5.1：真实车辆（linkMode 非 SIM）上报的经纬度为 WGS-84，
        // 需在入库前转 GCJ-02 以保证高德地图显示位置正确；
        // 仿真车辆（linkMode=SIM）上报的是 schematic x/y，跳过转换。
        if (!isSimulationVehicle(vehicleEntity)) {
            BigDecimal[] gcj = Wgs84Gcj02Converter.wgs84ToGcj02(
                    request.getLongitude(), request.getLatitude());
            vehicleEntity.setCurrentLongitude(gcj[0]);
            vehicleEntity.setCurrentLatitude(gcj[1]);
        } else {
            vehicleEntity.setCurrentLatitude(request.getLatitude());
            vehicleEntity.setCurrentLongitude(request.getLongitude());
        }
        vehicleEntity.setBatteryLevel(request.getBatteryLevel());
        vehicleEntity.setLastReportTime(request.getReportTime());
        vehicleMapper.updateById(vehicleEntity);
        return vehicleEntity;
    }

    /** 仿真车辆 linkMode=SIM 或为空（向后兼容历史数据）。 */
    private boolean isSimulationVehicle(VehicleEntity vehicleEntity) {
        String linkMode = vehicleEntity.getLinkMode();
        return linkMode == null || linkMode.isBlank()
                || VehicleLinkMode.SIM.name().equals(linkMode);
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
