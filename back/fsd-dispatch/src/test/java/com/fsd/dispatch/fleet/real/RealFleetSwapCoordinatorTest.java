package com.fsd.dispatch.fleet.real;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.entity.BatterySwapCabinetEntity;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

class RealFleetSwapCoordinatorTest {

    private BatterySwapSessionService swapSessionService;
    private BatterySwapCabinetMapper swapCabinetMapper;
    private FleetChargePolicy fleetChargePolicy;
    private DispatchStrategyRuntimeService strategyRuntimeService;
    private RealFleetSwapCoordinator coordinator;

    @BeforeEach
    void setUp() {
        swapSessionService = mock(BatterySwapSessionService.class);
        swapCabinetMapper = mock(BatterySwapCabinetMapper.class);
        fleetChargePolicy = mock(FleetChargePolicy.class);
        strategyRuntimeService = mock(DispatchStrategyRuntimeService.class);
        coordinator = new RealFleetSwapCoordinator(
                swapSessionService, swapCabinetMapper, fleetChargePolicy, strategyRuntimeService);
        ReflectionTestUtils.setField(coordinator, "defaultParkId", 1L);
    }

    @Test
    void shouldStartSwapSessionWhenEnteringSwappingStage() {
        VehicleEntity vehicle = vehicle(1L);
        BatterySwapCabinetEntity cabinet = cabinet(9L, "SWAP-01");
        when(fleetChargePolicy.isActivelySwapping("SWAPPING")).thenReturn(true);
        when(swapSessionService.findActiveByVehicle(1L)).thenReturn(Optional.empty());
        Page<BatterySwapCabinetEntity> cabinetPage = new Page<>();
        cabinetPage.setRecords(java.util.List.of(cabinet));
        when(swapCabinetMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(cabinetPage);

        coordinator.onTelemetry(vehicle, telemetry("SWAPPING", "SWAP-01", 18), null);

        verify(swapSessionService).startSession(eq(1L), eq(1L), eq(9L), eq(18));
    }

    @Test
    void shouldCompleteSwapSessionWhenLeavingSwappingStage() {
        VehicleEntity vehicle = vehicle(1L);
        FleetRuntime previous = FleetRuntime.builder().vehicleId(1L).runtimeStage("SWAPPING").build();
        when(fleetChargePolicy.isActivelySwapping("STANDBY")).thenReturn(false);
        when(fleetChargePolicy.isActivelySwapping("SWAPPING")).thenReturn(true);

        coordinator.onTelemetry(vehicle, telemetry("STANDBY", null, 100), previous);

        verify(swapSessionService).completeActiveSession(1L);
        verify(swapSessionService, never()).startSession(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void prefersSwapRecoveryWhenStrategyModeIsSwap() {
        FleetEnergyProperties energy = new FleetEnergyProperties();
        energy.setEnergyRecoveryMode("SWAP");
        when(strategyRuntimeService.energyForAssign(1L)).thenReturn(energy);
        Page<BatterySwapCabinetEntity> cabinetPage = new Page<>();
        cabinetPage.setRecords(java.util.List.of(cabinet(1L, "SWAP-01")));
        when(swapCabinetMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(cabinetPage);

        boolean prefers = coordinator.prefersSwapRecovery(vehicle(2L), 1L);

        org.junit.jupiter.api.Assertions.assertTrue(prefers);
    }

    private VehicleEntity vehicle(Long id) {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(id);
        vehicle.setVehicleCode("REAL-" + id);
        return vehicle;
    }

    private BatterySwapCabinetEntity cabinet(Long id, String code) {
        BatterySwapCabinetEntity cabinet = new BatterySwapCabinetEntity();
        cabinet.setId(id);
        cabinet.setCabinetCode(code);
        cabinet.setParkId(1L);
        return cabinet;
    }

    private VehicleTelemetryRequest telemetry(String stage, String targetCode, int soc) {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setVehicleCode("REAL-1");
        request.setRuntimeStage(stage);
        request.setTargetCode(targetCode);
        request.setSoc(soc);
        request.setX(BigDecimal.valueOf(100));
        request.setY(BigDecimal.valueOf(200));
        request.setReportTime(LocalDateTime.now());
        request.setEventSeq(1L);
        return request;
    }
}
