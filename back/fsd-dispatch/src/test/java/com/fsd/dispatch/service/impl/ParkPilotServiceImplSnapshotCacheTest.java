package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 性能优化方案 P0-2：整园快照的 500ms 请求合流。
 *
 * <p>要守的行为有两条，且互相拉扯：TTL 内**只组装一次**（这是省掉的那 41 次 SELECT / 70 次 Redis 往返），
 * 以及 TTL 一过**必须换新的**（500ms 是对大屏承诺的新鲜度上界，缓存不能变成第二份真相）。
 * 这里用真实时钟测过期，所以每个过期用例多等 600ms。
 */
@ExtendWith(MockitoExtension.class)
class ParkPilotServiceImplSnapshotCacheTest {

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

    @BeforeEach
    void oneSimVehicle() {
        when(vehicleMapper.selectList(any())).thenReturn(List.of(simVehicle()));
        when(parkPilotSimulationService.buildSnapshots(anyList()))
                .thenReturn(List.of(ParkVehicleSnapshotResponse.builder()
                        .vehicleCode("ZJF-AV-01").parkId(PARK_ID).build()));
    }

    private static VehicleEntity simVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(9001L);
        vehicle.setVehicleCode("ZJF-AV-01");
        vehicle.setParkId(PARK_ID);
        vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        vehicle.setDeleted(0);
        return vehicle;
    }

    @Test
    @DisplayName("TTL 内三次读只组装一次")
    void shouldAssembleOnceInsideTtl() {
        for (int i = 0; i < 3; i++) {
            service.listVehicleSnapshots(PARK_ID);
        }
        verify(vehicleMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("全园区与单园区是两个键，不共用一份结果")
    void shouldKeepAllParkKeyApartFromParkScopedKey() {
        service.listVehicleSnapshots();
        service.listVehicleSnapshots(PARK_ID);
        verify(vehicleMapper, times(2)).selectList(any());
    }

    @Test
    @DisplayName("过期后必须重读，不能把缓存变成第二份真相")
    void shouldRebuildAfterTtlExpires() throws InterruptedException {
        service.listVehicleSnapshots(PARK_ID);
        Thread.sleep(600L);
        service.listVehicleSnapshots(PARK_ID);
        verify(vehicleMapper, times(2)).selectList(any());
    }

    @Test
    @DisplayName("订单快照内嵌的车队组装走同一份缓存")
    void orderSnapshotShouldNotReAssembleTheFleet() {
        when(parkStationService.requirePark(PARK_ID)).thenReturn(park());
        when(parkStationService.listStations(PARK_ID)).thenReturn(List.<ParkStationResponse>of());
        when(orderMapper.selectList(any())).thenReturn(List.of());

        service.listOrderSnapshots(PARK_ID);
        service.listOrderSnapshots(PARK_ID);

        verify(vehicleMapper, times(1)).selectList(any());
        assertEquals(0, service.listOrderSnapshots(PARK_ID).getItems().size());
    }

    private static ParkEntity park() {
        ParkEntity park = new ParkEntity();
        park.setId(PARK_ID);
        return park;
    }
}
