package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminParkScopeServiceImpl implements AdminParkScopeService {

    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final ParkStationService parkStationService;

    public AdminParkScopeServiceImpl(OrderMapper orderMapper,
                                     DispatchTaskMapper dispatchTaskMapper,
                                     ParkStationService parkStationService) {
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.parkStationService = parkStationService;
    }

    @Override
    public boolean matchesOrder(Long orderId, Long parkId) {
        if (parkId == null) {
            return true;
        }
        if (orderId == null) {
            return false;
        }
        OrderEntity order = orderMapper.selectById(orderId);
        return order != null && parkId.equals(order.getParkId());
    }

    @Override
    public boolean matchesVehicle(VehicleAdminListItemResponse vehicle, Long parkId) {
        if (parkId == null || vehicle == null) {
            return true;
        }
        if (vehicle.getCurrentOrderId() != null) {
            return matchesOrder(vehicle.getCurrentOrderId(), parkId);
        }
        if (vehicle.getCurrentTaskId() != null) {
            DispatchTaskEntity task = dispatchTaskMapper.selectOne(new LambdaQueryWrapper<DispatchTaskEntity>()
                    .eq(DispatchTaskEntity::getId, vehicle.getCurrentTaskId())
                    .eq(DispatchTaskEntity::getDeleted, 0));
            return task != null && matchesOrder(task.getOrderId(), parkId);
        }
        return parkId.equals(resolveDefaultParkId());
    }

    @Override
    public boolean matchesVehicleSnapshot(ParkVehicleSnapshotResponse vehicle, Long parkId) {
        if (parkId == null || vehicle == null) {
            return true;
        }
        if (vehicle.getCurrentOrderId() != null) {
            return matchesOrder(vehicle.getCurrentOrderId(), parkId);
        }
        if (vehicle.getCurrentTaskId() != null) {
            DispatchTaskEntity task = dispatchTaskMapper.selectOne(new LambdaQueryWrapper<DispatchTaskEntity>()
                    .eq(DispatchTaskEntity::getId, vehicle.getCurrentTaskId())
                    .eq(DispatchTaskEntity::getDeleted, 0));
            return task != null && matchesOrder(task.getOrderId(), parkId);
        }
        return parkId.equals(resolveDefaultParkId());
    }

    @Override
    public Long resolveDefaultParkId() {
        return parkStationService.requireDefaultPark().getId();
    }

    @Override
    public OrderEntity findOrder(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderMapper.selectById(orderId);
    }
}
