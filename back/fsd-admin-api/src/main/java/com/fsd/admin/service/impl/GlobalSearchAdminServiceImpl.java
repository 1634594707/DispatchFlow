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
                .like(OrderEntity::getOrderNo, normalized)
                .orderByDesc(OrderEntity::getId);
        if (numericId != null) {
            orderQuery.or().eq(OrderEntity::getId, numericId);
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
                .like(DispatchTaskEntity::getTaskNo, normalized)
                .orderByDesc(DispatchTaskEntity::getId);
        if (numericId != null) {
            taskQuery.or().eq(DispatchTaskEntity::getId, numericId);
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
                .and(wrapper -> wrapper
                        .like(VehicleEntity::getVehicleCode, normalized)
                        .or()
                        .like(VehicleEntity::getVehicleName, normalized))
                .orderByDesc(VehicleEntity::getId);
        if (numericId != null) {
            vehicleQuery.or().eq(VehicleEntity::getId, numericId);
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
