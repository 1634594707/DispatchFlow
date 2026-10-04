package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.fleet.service.FleetSnapshotAssembler;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.ParkingFacilityService;
import com.fsd.dispatch.vo.ParkOrderSnapshotListResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 性能优化方案 P0-3：整园订单快照的读侧收敛。
 *
 * <p>这一项改的不是"上屏几条"（一直是 20 条），而是"为了这 20 条读多少行、回查多少次"。
 * 原来一次调用 = 一遍 t_order 全表 + 一遍 t_dispatch_task 全表 + 每行两次站点回查，
 * 实测稳态 41 次 SELECT，其中 40 次是站点。这里守的就是那 40 次没回来，以及
 * "窗被填满"必须让调用方看见。
 */
@ExtendWith(MockitoExtension.class)
class ParkPilotServiceImplOrderWindowTest {

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
    @Mock private ParkingFacilityService parkingFacilityService;

    @InjectMocks private ParkPilotServiceImpl service;

    private static final long PARK_ID = 1L;
    private static final long PICKUP_STATION_ID = 501L;
    private static final long DROPOFF_STATION_ID = 506L;

    @BeforeEach
    void parkWithTwoStationsAndOneVehicle() {
        ParkEntity park = new ParkEntity();
        park.setId(PARK_ID);
        when(parkStationService.requirePark(PARK_ID)).thenReturn(park);
        when(parkStationService.listStations(PARK_ID)).thenReturn(List.of(
                station(PICKUP_STATION_ID, "ZJF-PICK-01"),
                station(DROPOFF_STATION_ID, "ZJF-DROP-01")));
        // 订单读内嵌的那一次车队组装：给一台车，保证它走缓存而不是走空
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(9001L);
        vehicle.setVehicleCode("ZJF-AV-01");
        vehicle.setParkId(PARK_ID);
        vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        vehicle.setDeleted(0);
        when(vehicleMapper.selectList(any())).thenReturn(List.of(vehicle));
        when(parkPilotSimulationService.buildSnapshots(anyList()))
                .thenReturn(List.of(ParkVehicleSnapshotResponse.builder().vehicleCode("ZJF-AV-01").build()));
    }

    private static ParkStationResponse station(Long id, String code) {
        return ParkStationResponse.builder().parkId(PARK_ID).stationId(id).stationCode(code).build();
    }

    private static List<OrderEntity> orders(int count, int startId, LocalDateTime baseUpdatedAt) {
        List<OrderEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OrderEntity order = new OrderEntity();
            order.setId((long) startId + i);
            order.setOrderNo("ORD-" + order.getId());
            order.setStatus("WAITING_DISPATCH");
            order.setParkId(PARK_ID);
            order.setPickupPointId(PICKUP_STATION_ID);
            order.setDropoffPointId(DROPOFF_STATION_ID);
            order.setUpdatedAt(baseUpdatedAt.plusMinutes(i));
            order.setDeleted(0);
            list.add(order);
        }
        return list;
    }

    @Test
    @DisplayName("任务表不再全表读，站点不再逐行回查")
    void shouldDropTheFullTaskScanAndPerRowStationLookups() {
        when(orderMapper.selectList(any())).thenReturn(orders(3, 100, LocalDateTime.now().minusHours(1)));

        ParkOrderSnapshotListResponse response = service.listOrderSnapshots(PARK_ID);

        assertEquals(3, response.getItems().size());
        verify(dispatchTaskMapper, never()).selectList(any());
        verify(parkStationService, never()).requireStation(any());
        verify(parkStationService, times(1)).listStations(PARK_ID);
    }

    @Test
    @DisplayName("上屏仍是按 updatedAt 倒序的 20 条")
    void shouldShowMostRecentlyUpdatedTwenty() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        when(orderMapper.selectList(any())).thenReturn(orders(25, 200, base));

        ParkOrderSnapshotListResponse response = service.listOrderSnapshots(PARK_ID);

        assertEquals(20, response.getItems().size());
        // 候选窗里最新的一条是 id 224（base+24 分钟），倒序后它必须排第一
        assertEquals(224L, response.getItems().getFirst().getOrderId());
        assertEquals(205L, response.getItems().getLast().getOrderId());
        assertTrue(response.isTruncated(), "窗里有 25 条、只上屏 20 条 ⇒ 列表不代表全部");
    }

    @Test
    @DisplayName("候选窗被填满就是截断，哪怕本窗只匹配到 20 条以内")
    void shouldReportTruncatedWhenWindowExhausted() {
        when(orderMapper.selectList(any())).thenReturn(orders(60, 300, LocalDateTime.now().minusHours(3)));

        ParkOrderSnapshotListResponse response = service.listOrderSnapshots(PARK_ID);

        assertEquals(20, response.getItems().size());
        assertTrue(response.isTruncated());
    }

    @Test
    @DisplayName("窗没满且都上屏了，就不许报截断")
    void shouldNotReportTruncatedWhenEverythingFits() {
        when(orderMapper.selectList(any())).thenReturn(orders(8, 400, LocalDateTime.now().minusHours(4)));

        ParkOrderSnapshotListResponse response = service.listOrderSnapshots(PARK_ID);

        assertEquals(8, response.getItems().size());
        assertFalse(response.isTruncated());
    }

    @Test
    @DisplayName("别的园区的单不进这份快照")
    void shouldIgnoreOtherParkOrders() {
        List<OrderEntity> foreign = orders(2, 500, LocalDateTime.now().minusHours(1));
        foreign.forEach(order -> order.setParkId(999L));
        List<OrderEntity> mixed = new ArrayList<>(foreign);
        mixed.addAll(orders(1, 510, LocalDateTime.now().minusHours(1)));
        when(orderMapper.selectList(any())).thenReturn(mixed);

        ParkOrderSnapshotListResponse response = service.listOrderSnapshots(PARK_ID);

        assertEquals(1, response.getItems().size());
        assertFalse(response.isTruncated());
    }
}
