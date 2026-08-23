package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.admin.service.GlobalSearchAdminService;
import com.fsd.admin.vo.AdminGlobalSearchItem;
import com.fsd.admin.vo.AdminGlobalSearchResponse;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GlobalSearchAdminServiceImpl implements GlobalSearchAdminService {

    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final VehicleMapper vehicleMapper;

    public GlobalSearchAdminServiceImpl(OrderMapper orderMapper,
                                        DispatchTaskMapper dispatchTaskMapper,
                                        VehicleMapper vehicleMapper) {
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    public AdminGlobalSearchResponse search(String keyword, int limit) {
        return search(keyword, limit, null);
    }

    @Override
    public AdminGlobalSearchResponse search(String keyword, int limit, Long parkId) {
        String normalized = keyword == null ? "" : keyword.trim();
        // perType 为服务端计算的边界整数（3-10），用于分页 size；不再通过 .last("LIMIT " + n) 拼接。
        int perType = Math.max(3, Math.min(limit, 30) / 3);
        List<AdminGlobalSearchItem> items = new ArrayList<>();
        if (!StringUtils.hasText(normalized)) {
            return AdminGlobalSearchResponse.builder().keyword(normalized).items(items).build();
        }
        Long numericId = parseNumericId(normalized);
        // SEC-17 fix: use Page<> with a bounded size instead of .last("LIMIT " + n) string
        // concatenation. Even though perType is server-side bounded, this removes the
        // concatenation pattern entirely so future refactors cannot introduce injection.
        Page<OrderEntity> orderPage = new Page<>(1, perType);
        LambdaQueryWrapper<OrderEntity> orderQuery = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getDeleted, 0)
                .and(wrapper -> wrapper
                        .like(OrderEntity::getOrderNo, normalized)
                        .or()
                        .eq(numericId != null, OrderEntity::getId, numericId))
                .orderByDesc(OrderEntity::getId);
        if (parkId != null) {
            orderQuery.eq(OrderEntity::getParkId, parkId);
        }
        orderMapper.selectPage(orderPage, orderQuery)
                .getRecords()
                .forEach(order -> items.add(AdminGlobalSearchItem.builder()
                        .type("ORDER")
                        .id(order.getId())
                        .code(order.getOrderNo())
                        .title("订单 " + order.getOrderNo())
                        .subtitle("状态 " + order.getStatus())
                        .routePath("/orders/" + order.getId())
                        .build()));
        Page<DispatchTaskEntity> taskPage = new Page<>(1, perType);
        LambdaQueryWrapper<DispatchTaskEntity> taskQuery = new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .and(wrapper -> wrapper
                        .like(DispatchTaskEntity::getTaskNo, normalized)
                        .or()
                        .eq(numericId != null, DispatchTaskEntity::getId, numericId))
                .orderByDesc(DispatchTaskEntity::getId);
        if (parkId != null) {
            taskQuery.apply("order_id IN (SELECT id FROM t_order WHERE deleted = 0 AND park_id = {0})", parkId);
        }
        dispatchTaskMapper.selectPage(taskPage, taskQuery)
                .getRecords()
                .forEach(task -> items.add(AdminGlobalSearchItem.builder()
                        .type("TASK")
                        .id(task.getId())
                        .code(task.getTaskNo())
                        .title("任务 " + task.getTaskNo())
                        .subtitle("状态 " + task.getStatus())
                        .routePath("/tasks/" + task.getId())
                        .build()));
        Page<VehicleEntity> vehiclePage = new Page<>(1, perType);
        LambdaQueryWrapper<VehicleEntity> vehicleQuery = new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .and(wrapper -> wrapper
                        .like(VehicleEntity::getVehicleCode, normalized)
                        .or()
                        .like(VehicleEntity::getVehicleName, normalized)
                        .or()
                        .eq(numericId != null, VehicleEntity::getId, numericId))
                .orderByDesc(VehicleEntity::getId);
        if (parkId != null) {
            vehicleQuery.eq(VehicleEntity::getParkId, parkId);
        }
        vehicleMapper.selectPage(vehiclePage, vehicleQuery)
                .getRecords()
                .forEach(vehicle -> items.add(AdminGlobalSearchItem.builder()
                        .type("VEHICLE")
                        .id(vehicle.getId())
                        .code(vehicle.getVehicleCode())
                        .title(vehicle.getVehicleCode())
                        .subtitle(vehicle.getVehicleName())
                        .routePath("/vehicles/" + vehicle.getId())
                        .build()));
        return AdminGlobalSearchResponse.builder()
                .keyword(normalized)
                .items(items.stream().limit(limit).toList())
                .build();
    }

    private Long parseNumericId(String keyword) {
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
