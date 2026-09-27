package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.policy.FleetChargePolicyImpl;
import com.fsd.dispatch.fleet.policy.FleetEnergyThresholdResolver;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.BatterySwapSessionMapper;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.service.EnergyForecastService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P1-1 调度指标口径的直测：可用/忙碌/充电中/手动接管/低 SOC/backlog/供需比，
 * 全部来自 t_vehicle 现值 + Redis 运行态 + 补能策略出口，页面与导出共用同一条路径。
 */
class AnalyticsAdminServiceImplDispatchMetricsTest {

    private AnalyticsAdminServiceImpl service;
    private FleetRuntimeService fleetRuntimeService;
    private OrderMapper orderMapper;
    private com.fsd.dispatch.mapper.DispatchTaskMapper dispatchTaskMapper;

    @BeforeEach
    void setUp() {
        fleetRuntimeService = mock(FleetRuntimeService.class);
        orderMapper = mock(OrderMapper.class);
        dispatchTaskMapper = Mockito.mock(com.fsd.dispatch.mapper.DispatchTaskMapper.class);
        FleetEnergyProperties energyProperties = new FleetEnergyProperties();
        FleetChargePolicy chargePolicy = new FleetChargePolicyImpl(energyProperties,
                new FleetEnergyThresholdResolver(null, energyProperties));
        service = new AnalyticsAdminServiceImpl(
                orderMapper, dispatchTaskMapper,
                mock(com.fsd.dispatch.mapper.DispatchExceptionRecordMapper.class),
                mock(ChargingSessionMapper.class),
                mock(BatterySwapSessionMapper.class),
                mock(ChargingPileMapper.class),
                mock(VehicleMapper.class),
                fleetRuntimeService,
                mock(com.fsd.dispatch.mapper.ParkMapper.class),
                mock(com.fsd.admin.service.AdminParkScopeService.class),
                new SimpleMeterRegistry(),
                mock(EnergyForecastService.class),
                Mockito.mock(com.fsd.dispatch.config.EnergyForecastProperties.class),
                chargePolicy);
    }

    @Test
    @DisplayName("口径逐格核对：可用/忙碌/充电/手动/低SOC/backlog/供需比")
    void dispatchMetricsFollowTheDeclaredSemantics() {
        com.fsd.vehicle.entity.VehicleEntity v1 = vehicle(1L, "IDLE", 80);
        com.fsd.vehicle.entity.VehicleEntity v2 = vehicle(2L, "BUSY", 25);
        com.fsd.vehicle.entity.VehicleEntity v3 = vehicle(3L, "IDLE", 28);
        Map<Long, FleetRuntime> runtimes = Map.of(
                1L, runtime("STANDBY"),
                2L, runtime("CHARGING"),
                3L, runtime("MANUAL"));
        when(fleetRuntimeService.getBatch(List.of(1L, 2L, 3L))).thenReturn(runtimes);
        List<com.fsd.order.entity.OrderEntity> orders = List.of(
                order("WAITING_DISPATCH"), order("WAITING_DISPATCH"), order("COMPLETED"));

        var metrics = service.buildDispatchMetrics(List.of(v1, v2, v3), orders);

        assertEquals(1L, metrics.getAvailableVehicles(), "v1：IDLE 且 80 ≥ 30");
        assertEquals(1L, metrics.getBusyVehicles(), "v2：BUSY");
        assertEquals(1L, metrics.getChargingVehicles(), "v2：CHARGING");
        assertEquals(1L, metrics.getManualPendingVehicles(), "v3：MANUAL 接管");
        assertEquals(2L, metrics.getLowSocVehicles(), "v2=25、v3=28 都低于 30");
        assertEquals(2L, metrics.getPendingOrders(), "两笔 WAITING_DISPATCH");
        assertEquals(0.5D, metrics.getSupplyDemandRatio(), 1e-9, "1 可用 / 2 backlog");
    }

    @Test
    @DisplayName("站点×小时聚合：按 pickupNodeCode × createdAt 小时计数，空编码记未知")
    void stationHourRowsFollowTheDeclaredSemantics() {
        com.fsd.order.entity.OrderEntity a = order("COMPLETED");
        a.setPickupNodeCode("N-A");
        a.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 27, 9, 15));
        com.fsd.order.entity.OrderEntity b = order("COMPLETED");
        b.setPickupNodeCode("N-A");
        b.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 27, 9, 45));
        com.fsd.order.entity.OrderEntity c = order("COMPLETED");
        c.setPickupNodeCode("N-B");
        c.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 27, 10, 5));
        com.fsd.order.entity.OrderEntity d = order("COMPLETED");
        d.setPickupNodeCode(" ");
        d.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 27, 10, 30));

        var rows = service.buildStationHourRows(List.of(a, b, c, d));
        assertEquals(3, rows.size());
        assertEquals("N-A", rows.get(0).getStation());
        assertEquals(9, rows.get(0).getHour());
        assertEquals(2L, rows.get(0).getOrders());
        assertEquals("N-B", rows.get(1).getStation());
        assertEquals(10, rows.get(1).getHour());
        assertEquals("未知", rows.get(2).getStation());
        assertEquals(1L, rows.get(2).getOrders());
    }

    private com.fsd.vehicle.entity.VehicleEntity vehicle(Long id, String dispatchStatus, Integer soc) {
        com.fsd.vehicle.entity.VehicleEntity vehicle = new com.fsd.vehicle.entity.VehicleEntity();
        vehicle.setId(id);
        vehicle.setVehicleCode("SIM-" + id);
        vehicle.setOnlineStatus("ONLINE");
        vehicle.setDispatchStatus(dispatchStatus);
        vehicle.setBatteryLevel(soc);
        return vehicle;
    }

    private FleetRuntime runtime(String stage) {
        ParkingSlotEntity ignored = new ParkingSlotEntity();
        ignored.setSlotCode("X");
        FleetRuntime runtime = new FleetRuntime();
        runtime.setRuntimeStage(stage);
        return runtime;
    }

    private com.fsd.order.entity.OrderEntity order(String status) {
        com.fsd.order.entity.OrderEntity order = new com.fsd.order.entity.OrderEntity();
        order.setOrderNo("OD-" + status + "-" + System.nanoTime());
        order.setStatus(status);
        return order;
    }
}
