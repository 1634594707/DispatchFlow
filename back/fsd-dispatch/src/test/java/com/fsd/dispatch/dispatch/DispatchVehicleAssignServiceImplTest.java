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
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.dispatch.geo.DispatchGeoDistanceService;
import com.fsd.dispatch.mapf.MapfRoutePlannerService;
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
    @Mock
    private ChargingSessionService chargingSessionService;
    @Mock
    private com.fsd.dispatch.service.DispatchDecisionSnapshotService decisionSnapshotService;

    private final DispatchGeoDistanceService dispatchGeoDistanceService = new DispatchGeoDistanceService(
            null, null, null, null, null) {
        @Override
        public boolean isGeoBlendEnabled() {
            return false;
        }

        @Override
        public List<Double> applyGeoBlend(List<VehicleEntity> candidates,
                                          ParkStationResponse pickup,
                                          List<Double> parkDistancesPx) {
            return parkDistancesPx;
        }
    };

    private final MapfRoutePlannerService mapfRoutePlannerService = new MapfRoutePlannerService(
            null, null, null, null) {
        @Override
        public boolean isEnabled() {
            return false;
        }
    };

    private DispatchVehicleAssignServiceImpl assignService;

    @BeforeEach
    void setUp() {
        FleetEnergyProperties energy = new FleetEnergyProperties();
        DispatchScoringProperties scoring = new DispatchScoringProperties();
        lenient().when(strategyRuntimeService.strategyForAssign(any(), any()))
                .thenReturn(new DispatchStrategyRuntimeService.AssignStrategy(
                        energy, scoring, null, null, null, 0, true));
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
        lenient().when(chargingSessionService.estimateDistanceToNearestChargingPile(any(), any(), any())).thenReturn(200.0);
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
                automationRuleService,
                dispatchGeoDistanceService,
                mapfRoutePlannerService,
                chargingSessionService,
                new com.fsd.dispatch.fleet.policy.TelemetryFreshnessPolicy(30),
                decisionSnapshotService,
                new com.fsd.dispatch.core.RulePolicy());
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
        // 遥测新鲜度门禁要求候选车辆有最新上报（路线图 5.1）
        low.setLastReportTime(java.time.LocalDateTime.now());
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(low));

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.LOW_SOC, result.getFailReason());
    }

    /**
     * §7.2 的标签错位：维保/车型/车队池/配送区/载重以前和 SOC 挤在同一层，任何一条不满足都对外报
     * LOW_SOC ⇒ "派单失败原因分布"里的低电量占比吞掉了别的成因。现在拆开各报各的，
     * 并把最先把候选清零的那一层写进消息（筛选与诊断共用同一张过滤器表，不会与实际判定漂移）。
     */
    @Test
    void constraintMismatchMustNotBeReportedAsLowSoc() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        VehicleEntity maintained = vehicle(1L, "ZJF-AV-01", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(700));
        maintained.setDispatchStatus("UNAVAILABLE");
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(maintained));

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.NO_MATCHING_VEHICLE, result.getFailReason(),
                "电量够、约束不满足却报 LOW_SOC，失败原因分布就不可信了");
        assertTrue(result.getMessage().contains("binding: MAINTENANCE"), result.getMessage());
        assertTrue(result.getMessage().contains("survivors per filter {MAINTENANCE=0}"),
                "应报出逐层存活数，便于一眼看出卡在哪一层：" + result.getMessage());
    }

    /** 混合车队：一台低电 + 一台维保 —— 旧口径下两台都被算成"电量不足"，这里必须分开。 */
    @Test
    void lowSocStillReportsLowSocOnlyWhenTheSocFilterIsWhatEmptiesCandidates() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        VehicleEntity low = vehicle(1L, "ZJF-AV-01", 5, BigDecimal.valueOf(100), BigDecimal.valueOf(700));
        VehicleEntity maintained = vehicle(2L, "ZJF-AV-02", 100, BigDecimal.valueOf(120), BigDecimal.valueOf(710));
        maintained.setDispatchStatus("UNAVAILABLE");
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(low, maintained));

        DispatchAssignResult constraint = assignService.selectBestVehicle(order);
        assertEquals(DispatchAssignFailReason.NO_MATCHING_VEHICLE, constraint.getFailReason(),
                "SOC 过关还剩一台活着 ⇒ 卡住它的是约束，不是电量");

        VehicleEntity only = vehicle(3L, "ZJF-AV-03", 5, BigDecimal.valueOf(100), BigDecimal.valueOf(700));
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(only));
        DispatchAssignResult soc = assignService.selectBestVehicle(order);
        assertEquals(DispatchAssignFailReason.LOW_SOC, soc.getFailReason(), "真·电量不足仍要报 LOW_SOC");
    }

    @Test
    void shouldFailWhenPickupUnreachable() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);
        VehicleEntity vehicle = vehicle(1L, "ZJF-AV-01", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(700));
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

        VehicleEntity far = vehicle(1L, "ZJF-AV-01", 100, BigDecimal.valueOf(10), BigDecimal.valueOf(10));
        VehicleEntity near = vehicle(2L, "ZJF-AV-02", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

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
        assertEquals("ZJF-AV-01", result.getVehicleCode());
    }

    @Test
    void shouldFailWhenParkIdNull() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(null);

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.NO_VEHICLE, result.getFailReason());
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
        // 遥测新鲜度门禁要求候选车辆有最新上报（路线图 5.1）
        entity.setLastReportTime(java.time.LocalDateTime.now());
        return entity;
    }

    @Test
    void shouldRejectStaleTelemetryVehicleWithDedicatedReason() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);

        VehicleEntity stale = vehicle(9L, "ZJF-AV-09", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(150));
        stale.setLastReportTime(java.time.LocalDateTime.now().minusSeconds(600));
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(stale));

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.TELEMETRY_STALE, result.getFailReason());
    }

    @Test
    void shouldRejectVehicleWithoutAnyTelemetryReport() {
        OrderEntity order = new OrderEntity();
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);
        order.setParkId(1L);

        VehicleEntity neverReported = vehicle(8L, "ZJF-AV-08", 100, BigDecimal.valueOf(100), BigDecimal.valueOf(150));
        neverReported.setLastReportTime(null);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(neverReported));

        DispatchAssignResult result = assignService.selectBestVehicle(order);

        assertFalse(result.isSuccess());
        assertEquals(DispatchAssignFailReason.TELEMETRY_STALE, result.getFailReason());
    }
}
