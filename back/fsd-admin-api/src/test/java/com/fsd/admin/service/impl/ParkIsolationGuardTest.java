package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 2.1 / 8.1：跨园区隔离自动化契约。
 *
 * <p>园区 A 的订单/任务/车辆快照不得命中园区 B 的作用域判定；
 * parkId 为空表示全局视图，放行所有记录。</p>
 */
class ParkIsolationGuardTest {

    private static final long PARK_A = 1L;
    private static final long PARK_B = 2L;

    private OrderMapper orderMapper;
    private DispatchTaskMapper dispatchTaskMapper;
    private ParkStationService parkStationService;
    private AdminParkScopeServiceImpl scopeService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        parkStationService = mock(ParkStationService.class);
        scopeService = new AdminParkScopeServiceImpl(orderMapper, dispatchTaskMapper, parkStationService);
    }

    private OrderEntity orderIn(Long parkId) {
        OrderEntity order = new OrderEntity();
        order.setId(100L);
        order.setParkId(parkId);
        when(orderMapper.selectById(100L)).thenReturn(order);
        return order;
    }

    @Test
    void orderInParkAMustNotMatchParkBScope() {
        orderIn(PARK_A);
        assertTrue(scopeService.matchesOrder(100L, PARK_A));
        assertFalse(scopeService.matchesOrder(100L, PARK_B), "园区A订单不得命中园区B");
    }

    @Test
    void missingOrderMustBeRejectedUnderScopedPark() {
        when(orderMapper.selectById(404L)).thenReturn(null);
        assertFalse(scopeService.matchesOrder(404L, PARK_A));
        assertFalse(scopeService.matchesOrder(404L, PARK_B));
        assertTrue(scopeService.matchesOrder(404L, null), "全局视图放行");
    }

    @Test
    void vehicleWithExplicitParkFollowsOwnParkOnly() {
        VehicleAdminListItemResponse vehicleA = VehicleAdminListItemResponse.builder()
                .vehicleId(1L)
                .parkId(PARK_A)
                .build();
        assertTrue(scopeService.matchesVehicle(vehicleA, PARK_A));
        assertFalse(scopeService.matchesVehicle(vehicleA, PARK_B));
    }

    @Test
    void vehicleWithoutParkFallsBackToOrderThenTaskChain() {
        orderIn(PARK_B);
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(9L);
        task.setOrderId(100L);
        task.setDeleted(0);
        when(dispatchTaskMapper.selectOne(any())).thenReturn(task);

        // 无园区、有订单 → 按订单园区判定
        VehicleAdminListItemResponse viaOrder = VehicleAdminListItemResponse.builder()
                .vehicleId(2L)
                .currentOrderId(100L)
                .build();
        assertTrue(scopeService.matchesVehicle(viaOrder, PARK_B));
        assertFalse(scopeService.matchesVehicle(viaOrder, PARK_A));

        // 无园区、无订单、有任务 → 沿任务回溯订单园区
        VehicleAdminListItemResponse viaTask = VehicleAdminListItemResponse.builder()
                .vehicleId(3L)
                .currentTaskId(9L)
                .build();
        assertTrue(scopeService.matchesVehicle(viaTask, PARK_B));
        assertFalse(scopeService.matchesVehicle(viaTask, PARK_A));
    }

    @Test
    void vehicleSnapshotFollowsSameIsolationSemantics() {
        orderIn(PARK_B);
        ParkVehicleSnapshotResponse snapshotB = ParkVehicleSnapshotResponse.builder()
                .vehicleId(5L)
                .currentOrderId(100L)
                .build();
        assertTrue(scopeService.matchesVehicleSnapshot(snapshotB, PARK_B));
        assertFalse(scopeService.matchesVehicleSnapshot(snapshotB, PARK_A));

        ParkVehicleSnapshotResponse explicitA = ParkVehicleSnapshotResponse.builder()
                .vehicleId(6L)
                .parkId(PARK_A)
                .build();
        assertTrue(scopeService.matchesVehicleSnapshot(explicitA, PARK_A));
        assertFalse(scopeService.matchesVehicleSnapshot(explicitA, PARK_B));
    }
}