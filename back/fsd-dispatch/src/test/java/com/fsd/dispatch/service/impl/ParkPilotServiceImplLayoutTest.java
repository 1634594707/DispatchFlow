package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.fleet.service.FleetSnapshotAssembler;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.ParkingFacilityService;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * `/park/layout` 的车位层来自哪张表（路线图 §16.11①）。
 *
 * <p>这条门看着小，但它守的是"车为什么看起来乱停"：真 35 个待命位一直躺在 {@code t_parking_slot} 里，
 * 而响应给的是 {@code application.yml} 的 P1..P6 —— 那组是老示意图像素坐标，画到现役画布上就是错的。
 */
@ExtendWith(MockitoExtension.class)
class ParkPilotServiceImplLayoutTest {

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

    @Test
    void parkingSpotsComeFromTheSlotTableNotFromYaml() {
        when(parkStationService.requirePark(PARK_ID)).thenReturn(park());
        when(parkStationService.listStations(PARK_ID)).thenReturn(List.of());
        when(parkingFacilityService.listSlotMarkers(PARK_ID)).thenReturn(List.of(
                slot("P1", "STANDBY", "FREE", null),
                slot("E1B1", "CHARGING_ONLY", "OCCUPIED", 42L)));

        ParkLayoutResponse layout = service.getLayout(PARK_ID);

        assertEquals(List.of("P1", "E1B1"), layout.getParkingSpots().stream().map(ParkPointResponse::getCode).toList());
        ParkPointResponse occupied = layout.getParkingSpots().get(1);
        assertEquals("CHARGING_ONLY", occupied.getSlotType());
        assertEquals("OCCUPIED", occupied.getStatus());
        assertEquals(42L, occupied.getOccupiedVehicleId(), "点车位要能说出是谁占着，所以占用者必须跟着出来");
        // 反证：yml 那组老像素点不能再被读一次
        verify(parkPilotProperties, never()).getParkingSpots();
    }

    private static ParkEntity park() {
        ParkEntity park = new ParkEntity();
        park.setId(PARK_ID);
        park.setParkCode("DEFAULT");
        return park;
    }

    private static ParkPointResponse slot(String code, String slotType, String status, Long vehicleId) {
        return ParkPointResponse.builder()
                .code(code)
                .x(new BigDecimal("550.12"))
                .y(new BigDecimal("406.67"))
                .longitude(new BigDecimal("121.0800810"))
                .latitude(new BigDecimal("31.9605916"))
                .slotType(slotType)
                .status(status)
                .occupiedVehicleId(vehicleId)
                .build();
    }
}
