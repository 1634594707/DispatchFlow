package com.fsd.dispatch.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.dispatch.service.HubCapacityService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.dispatch.service.TrafficZoneControlService;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchVehicleAssignServiceImplTest {

    @Mock
    private VehicleService vehicleService;
    @Mock
    private ParkStationService parkStationService;
    @Mock
    private ParkRoutePlannerService parkRoutePlannerService;
    @Mock
    private FleetRuntimeService fleetRuntimeService;
    @Mock
    private DispatchStrategyRuntimeService strategyRuntimeService;
    @Mock
    private TrafficZoneControlService trafficZoneControlService;
    @Mock
    private DispatchPauseControlService dispatchPauseControlService;
    @Mock
    private HubCapacityService hubCapacityService;
    @Mock
    private DispatchRouteService dispatchRouteService;
    @Mock
    private PeakModeService peakModeService;
    @Mock
    private DispatchAutomationRuleService automationRuleService;

    private DispatchVehicleAssignServiceImpl assignService;

    @BeforeEach
    void setUp() {
        FleetEnergyProperties energy = new FleetEnergyProperties();
        DispatchScoringProperties scoring = new DispatchScoringProperties();
        lenient().when(strategyRuntimeService.energyForAssign(any())).thenReturn(energy);
        lenient().when(strategyRuntimeService.scoringForAssign(any())).thenReturn(scoring);
        lenient().when(trafficZoneControlService.isPointInPausedZone(any(), any(), any())).thenReturn(false);
        lenient().when(dispatchPauseControlService.isDispatchPaused(any())).thenReturn(false);
        lenient().when(hubCapacityService.isHubLikeStation(any())).thenReturn(false);
        lenient().when(hubCapacityService.isHubCapacityAvailable(any())).thenReturn(true);
        lenient().when(peakModeService.isPeakMode(any())).thenReturn(false);
        lenient().when(automationRuleService.resolvePeakDistanceFactor(any(), any(Double.class))).thenReturn(1.0);
        lenient().when(parkStationService.requireStation(any())).thenAnswer(invocation -> {
            Long stationId = invocation.getArgument(0);
            return station(stationId, 1L);
        });
        assignService = new DispatchVehicleAssignServiceImpl(
                vehicleService,
                parkStationService,
                parkRoutePlannerService,
                strategyRuntimeService,
                fleetRuntimeService,
                trafficZoneControlService,
                dispatchPauseControlService,
                hubCapacityService,
                dispatchRouteService,
                peakModeService,
                automationRuleService);
    }

    @Test
    void shouldFailWhenNoIdleVehicle() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of());

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.NO_VEHICLE, result.getFailReason());
    }

    @Test
    void shouldFailWhenLowSoc() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        VehicleEntity low = new VehicleEntity();
        low.setId(1L);
        low.setBatteryLevel(10);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(low));

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.LOW_SOC, result.getFailReason());
    }

    @Test
    void shouldFailWhenPickupUnreachable() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        VehicleEntity vehicle = vehicle(1L, "PARK-01", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(700));
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(vehicle));
        when(parkRoutePlannerService.isReachable(any(), any(), any(), any(), any())).thenReturn(false);

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.UNREACHABLE, result.getFailReason());
    }

    @Test
    void shouldPreferPluggedStandbyVehicle() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);

        VehicleEntity far = vehicle(1L, "PARK-01", 100, BigDecimal.valueOf(10), BigDecimal.valueOf(10));
        VehicleEntity near = vehicle(2L, "PARK-02", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(far, near));
        when(parkRoutePlannerService.isReachable(any(), any(), any(), any(), any())).thenReturn(true);
        when(parkRoutePlannerService.buildRoute(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        ParkPointResponse.builder().x(BigDecimal.ZERO).y(BigDecimal.ZERO).build(),
                        ParkPointResponse.builder().x(BigDecimal.TEN).y(BigDecimal.ZERO).build()));
        when(fleetRuntimeService.get(1L)).thenReturn(Optional.of(FleetRuntime.builder()
                .pluggedIn(true).runtimeStage("STANDBY").build()));
        when(fleetRuntimeService.get(2L)).thenReturn(Optional.empty());

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertTrue(result.isSuccess());
        assertEquals("PARK-01", result.getVehicleCode());
    }

    private static ParkStationResponse station(Long id, Long parkId) {
        return ParkStationResponse.builder()
                .stationId(id)
                .parkId(parkId)
                .x(BigDecimal.valueOf(200))
                .y(BigDecimal.valueOf(200))
                .build();
    }

    private static VehicleEntity vehicle(Long id, String code, int soc, BigDecimal x, BigDecimal y) {
        VehicleEntity entity = new VehicleEntity();
        entity.setId(id);
        entity.setVehicleCode(code);
        entity.setBatteryLevel(soc);
        entity.setCurrentLongitude(x);
        entity.setCurrentLatitude(y);
        return entity;
    }
}
