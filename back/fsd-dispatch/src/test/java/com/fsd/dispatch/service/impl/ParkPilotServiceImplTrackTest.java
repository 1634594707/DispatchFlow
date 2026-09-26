package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.fleet.service.FleetSnapshotAssembler;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkTrackResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * `/admin/park/track` 的读模型门（路线图 §16.3 / §16.8）。
 *
 * <p>为什么值得单独立一个门：这是个**匿名可读**的端点（移动页没有 token），而它新引入了一个
 * 调用方可控的入参 {@code orderId}。匿名接口上任何"按 id 取一条"的能力都必须先证明
 * 它跨不了园区 —— 否则整园读法反而成了更安全的那一个。
 */
@ExtendWith(MockitoExtension.class)
class ParkPilotServiceImplTrackTest {

    @Mock private ParkPilotProperties parkPilotProperties;
    @Mock private ParkStationService parkStationService;
    @Mock private ParkPilotSimulationService parkPilotSimulationService;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private ParkRoutePlannerService parkRoutePlannerService;
    @Mock private OrderMapper orderMapper;
    @Mock private DispatchTaskMapper dispatchTaskMapper;
    @Mock private FleetRuntimeService fleetRuntimeService;
    @Mock private FleetSnapshotAssembler fleetSnapshotAssembler;
    @Mock private ParkGeofenceService parkGeofenceService;
    @Mock private com.fsd.dispatch.geo.VehiclePositionResolver vehiclePositionResolver;

    @InjectMocks private ParkPilotServiceImpl service;

    private static final long PARK_ID = 1L;
    private static final long OTHER_PARK_ID = 2L;
    private static final long PICKUP_STATION_ID = 501L;
    private static final long DROPOFF_STATION_ID = 506L;
    private static final long ORDER_ID = 4001L;
    private static final long TASK_ID = 7001L;
    private static final long VEHICLE_ID = 9001L;

    @BeforeEach
    void parkHasTwoStations() {
        when(parkStationService.requirePark(PARK_ID)).thenReturn(park(PARK_ID));
        when(parkStationService.listStations(PARK_ID)).thenReturn(List.of(
                station(PICKUP_STATION_ID, "ZJF-PICK-01"),
                station(DROPOFF_STATION_ID, "ZJF-DROP-01")));
    }

    @Test
    void trackedOrderReturnsItsOwnVehicleSnapshotInsteadOfTheWholeFleet() {
        stubRecentQuery(List.of(order(ORDER_ID, PARK_ID)));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, PARK_ID));
        when(dispatchTaskMapper.selectById(TASK_ID)).thenReturn(task());
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle());
        when(parkPilotSimulationService.buildSnapshots(anyList()))
                .thenReturn(List.of(vehicleSnapshot()));
        when(orderMapper.selectCount(any())).thenReturn(3L);

        ParkTrackResponse response = service.buildTrackSnapshot(PARK_ID, ORDER_ID, 8);

        assertNotNull(response.getOrder());
        assertEquals(ORDER_ID, response.getOrder().getOrderId());
        assertEquals(VEHICLE_ID, response.getOrder().getVehicleId());
        assertNotNull(response.getVehicle(), "派给这一单的车必须在这一条响应里回来");
        assertEquals(3L, response.getActiveCount(), "页头的\"N 单配送中\"用服务端的计数，不用截过的列表长度");
        assertEquals(1, response.getRecentOrders().size());
        // 这条是这次改动的全部意义：车侧只构建**一辆**，不是把 35 台连折线一起装配回来
        verify(parkPilotSimulationService).buildSnapshots(anyList());
        verify(vehicleMapper).selectById(VEHICLE_ID);
    }

    @Test
    void anotherParksOrderIsNotReadableById() {
        stubRecentQuery(List.of());
        // 别的园区的单：站点不属本园区 ⇒ matchesParkOrder 判不过（与整园读法同一套口径）
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(ORDER_ID, OTHER_PARK_ID));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.buildTrackSnapshot(PARK_ID, ORDER_ID, 8));

        assertEquals("PARK_SCOPE_DENIED", ex.getCode());
        verify(parkPilotSimulationService, never()).buildSnapshots(anyList());
    }

    @Test
    void missingOrderIsReportedAsNotFoundNotAsAnEmptyEnvelope() {
        stubRecentQuery(List.of());
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.buildTrackSnapshot(PARK_ID, ORDER_ID, 8));

        assertEquals("ORDER_NOT_FOUND", ex.getCode());
    }

    @Test
    void withoutOrderIdItPicksTheNewestUnfinishedOrderAndNoVehicleYet() {
        stubRecentQuery(List.of(order(ORDER_ID, PARK_ID), order(ORDER_ID - 1, PARK_ID)));
        when(dispatchTaskMapper.selectById(TASK_ID)).thenReturn(null);
        when(orderMapper.selectCount(any())).thenReturn(2L);

        ParkTrackResponse response = service.buildTrackSnapshot(PARK_ID, null, 8);

        // 候选按主键倒序给出，第一条就是"最新那一单"；它还没有任务 ⇒ 没有车，但响应仍然是成功形状
        assertEquals(ORDER_ID, response.getOrder().getOrderId());
        assertNull(response.getVehicle());
        verify(vehicleMapper, never()).selectById(any());
    }

    private void stubRecentQuery(List<OrderEntity> candidates) {
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(candidates);
    }

    private static ParkEntity park(Long id) {
        ParkEntity park = new ParkEntity();
        park.setId(id);
        return park;
    }

    private static ParkStationResponse station(Long id, String code) {
        return ParkStationResponse.builder().parkId(PARK_ID).stationId(id).stationCode(code).area("ZJF").build();
    }

    private static OrderEntity order(Long id, Long parkId) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setOrderNo("ORD-" + id);
        order.setStatus("EXECUTING");
        order.setParkId(parkId);
        order.setPickupPointId(PICKUP_STATION_ID);
        order.setDropoffPointId(DROPOFF_STATION_ID);
        order.setDispatchTaskId(TASK_ID);
        order.setDeleted(0);
        return order;
    }

    private static DispatchTaskEntity task() {
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(TASK_ID);
        task.setTaskNo("TSK-" + TASK_ID);
        task.setOrderId(ORDER_ID);
        task.setVehicleId(VEHICLE_ID);
        task.setStatus("ASSIGNED");
        task.setDeleted(0);
        return task;
    }

    private static VehicleEntity vehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(VEHICLE_ID);
        vehicle.setVehicleCode("ZJF-AV-01");
        vehicle.setVehicleName("无人车 01");
        vehicle.setParkId(PARK_ID);
        vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
        vehicle.setDispatchStatus(VehicleDispatchStatus.BUSY.name());
        vehicle.setDeleted(0);
        return vehicle;
    }

    private static ParkVehicleSnapshotResponse vehicleSnapshot() {
        return ParkVehicleSnapshotResponse.builder()
                .vehicleId(VEHICLE_ID)
                .vehicleCode("ZJF-AV-01")
                .currentTaskId(TASK_ID)
                .currentOrderId(ORDER_ID)
                .linkMode(VehicleLinkMode.SIM.name())
                .runtimeStage("HEADING_TO_PICKUP")
                .build();
    }
}
