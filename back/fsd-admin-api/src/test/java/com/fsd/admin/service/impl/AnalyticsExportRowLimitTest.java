package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.mapper.BatterySwapSessionMapper;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 路线图 Phase 5：导出数据量上限 —— 超限返回 EXPORT_ROW_LIMIT_EXCEEDED 并引导使用报表计划。
 */
class AnalyticsExportRowLimitTest {

    private OrderMapper orderMapper;
    private AnalyticsAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        service = new AnalyticsAdminServiceImpl(
                orderMapper,
                mock(DispatchTaskMapper.class),
                mock(DispatchExceptionRecordMapper.class),
                mock(ChargingSessionMapper.class),
                mock(BatterySwapSessionMapper.class),
                mock(ChargingPileMapper.class),
                mock(VehicleMapper.class),
                mock(FleetRuntimeService.class),
                mock(ParkMapper.class),
                mock(AdminParkScopeService.class));
        ReflectionTestUtils.setField(service, "exportMaxRows", 2);
    }

    private OrderEntity order(String orderNo, LocalDateTime createdAt) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setStatus("COMPLETED");
        entity.setPriority("P1");
        entity.setDeleted(0);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void exportWithinLimitShouldReturnAllRows() {
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order("O1", LocalDateTime.now().minusDays(1)),
                order("O2", LocalDateTime.now().minusDays(2))));

        String csv = service.exportCsv("orders", "week", null);

        assertEquals(3, csv.split("\n").length, "表头 + 2 行数据");
        assertTrue(csv.startsWith("orderNo,status,priority,createdAt"));
    }

    @Test
    void exportBeyondLimitShouldFailWithGuidance() {
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order("O1", LocalDateTime.now().minusDays(1)),
                order("O2", LocalDateTime.now().minusDays(2)),
                order("O3", LocalDateTime.now().minusDays(3))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.exportCsv("orders", "week", null));
        assertEquals("EXPORT_ROW_LIMIT_EXCEEDED", ex.getCode());
        assertTrue(ex.getMessage().contains("报表计划"));
    }
}