package com.fsd.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        if (!tryOccupyVehicle(vehicleId, taskId, orderId)) {
            throw new BusinessException("VEHICLE_NOT_ASSIGNABLE", "Vehicle is not assignable");
        }
    }

    @Override
    @Transactional
    public boolean tryOccupyVehicle(Long vehicleId, Long taskId, Long orderId) {
        // 条件更新本身就是抢占锁：状态不对/已被别人占走 ⇒ 0 行，属预期结果，不抛。
        int updated = vehicleMapper.update(null, new LambdaUpdateWrapper<VehicleEntity>()
                .eq(VehicleEntity::getId, vehicleId)
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getOnlineStatus, VehicleOnlineStatus.ONLINE.name())
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.IDLE.name())
                .set(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.BUSY.name())
                .set(VehicleEntity::getCurrentTaskId, taskId)
                .set(VehicleEntity::getCurrentOrderId, orderId));
        return updated == 1;
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
        // 坐标语义按 linkMode 逐行判定（§7.2 定约）：真车行存 GCJ-02、仿真行存 schematic 像素。
        // 这里刻意不做任何换算 —— 上报侧（VehicleGatewayServiceImpl.resolveGcj02）已保证真车进来的
        // 就是 GCJ-02；再转一次 WGS-84→GCJ-02 等于把园区像素当经纬度处理，落库即垃圾坐标。
        // 取位统一走 geo/VehiclePositionResolver.toPark/toGeo。
        vehicleEntity.setCurrentLongitude(request.getLongitude());
        vehicleEntity.setCurrentLatitude(request.getLatitude());
        vehicleEntity.setBatteryLevel(request.getBatteryLevel());
        vehicleEntity.setLastReportTime(request.getReportTime());
        vehicleMapper.updateById(vehicleEntity);
        return vehicleEntity;
    }

    @Override
    public VehicleSummaryResponse getSummary() {
        return getSummary(null);
    }

    @Override
    public VehicleSummaryResponse getSummary(Long parkId) {
        long onlineCount = countByStatus(VehicleOnlineStatus.ONLINE.name(), null, parkId);
        long idleCount = countByStatus(null, VehicleDispatchStatus.IDLE.name(), parkId);
        long busyCount = countByStatus(null, VehicleDispatchStatus.BUSY.name(), parkId);
        return VehicleSummaryResponse.builder()
                .onlineCount(onlineCount)
                .idleCount(idleCount)
                .busyCount(busyCount)
                .build();
    }

    private long countByStatus(String onlineStatus, String dispatchStatus, Long parkId) {
        LambdaQueryWrapper<VehicleEntity> wrapper = new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0);
        if (onlineStatus != null) wrapper.eq(VehicleEntity::getOnlineStatus, onlineStatus);
        if (dispatchStatus != null) wrapper.eq(VehicleEntity::getDispatchStatus, dispatchStatus);
        if (parkId != null) wrapper.eq(VehicleEntity::getParkId, parkId);
        Long count = vehicleMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }
}
