package com.fsd.dispatch.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.fleet.real.RealFleetAdapter;
import com.fsd.dispatch.fleet.real.RealFleetSwapCoordinator;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.service.FleetTelemetryPersistenceService;
import com.fsd.dispatch.service.impl.VehicleGatewayServiceImpl;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class Phase3AcceptanceTest {

    private RealFleetSwapCoordinator noopSwapCoordinator() {
        RealFleetSwapCoordinator coordinator = new RealFleetSwapCoordinator(
                mock(BatterySwapSessionService.class),
                mock(BatterySwapCabinetMapper.class),
                mock(FleetChargePolicy.class),
                mock(DispatchStrategyRuntimeService.class));
        ReflectionTestUtils.setField(coordinator, "defaultParkId", 1L);
        return coordinator;
    }

    @Test
    void realFleetAdapterShouldPersistTelemetry() {
        FleetRuntimeService fleetRuntimeService = mock(FleetRuntimeService.class);
        when(fleetRuntimeService.get(1L)).thenReturn(Optional.empty());

        RealFleetAdapter adapter = new RealFleetAdapter(
                fleetRuntimeService, mock(FleetTelemetryPersistenceService.class), noopSwapCoordinator());
        adapter.ingestTelemetry(realVehicle(), telemetryRequest(1001L));

        ArgumentCaptor<FleetRuntime> captor = ArgumentCaptor.forClass(FleetRuntime.class);
        verify(fleetRuntimeService).save(captor.capture());
        assertEquals("STANDBY", captor.getValue().getRuntimeStage());
        assertEquals(95, captor.getValue().getSoc());
    }

    @Test
    void realVehicleTelemetryShouldBeIdempotent() {
        VehicleService vehicleService = mock(VehicleService.class);
        VehicleReportService vehicleReportService = mock(VehicleReportService.class);
        FleetRuntimeService fleetRuntimeService = mock(FleetRuntimeService.class);
        VehicleTelemetryIdempotencyService idempotencyService = mock(VehicleTelemetryIdempotencyService.class);
        RealFleetAdapter realFleetAdapter = new RealFleetAdapter(
                fleetRuntimeService, mock(FleetTelemetryPersistenceService.class), noopSwapCoordinator());
        FleetAdapterRegistry registry = new FleetAdapterRegistry(java.util.List.of(realFleetAdapter));

        VehicleGatewayServiceImpl gatewayService = new VehicleGatewayServiceImpl(
                vehicleService, vehicleReportService, registry, idempotencyService);

        VehicleEntity vehicle = realVehicle();
        VehicleTelemetryRequest request = telemetryRequest(2002L);
        when(idempotencyService.markIfFirstTelemetry(request)).thenReturn(true, false);
        when(vehicleService.getByVehicleCode("REAL-001")).thenReturn(vehicle);
        when(vehicleService.updateSnapshot(any())).thenReturn(vehicle);
        when(fleetRuntimeService.get(1L)).thenReturn(Optional.empty());

        gatewayService.ingestTelemetry(request);
        gatewayService.ingestTelemetry(request);

        verify(fleetRuntimeService, times(1)).save(any(FleetRuntime.class));
    }

    @Test
    void simVehicleShouldBeRejectedByGateway() {
        VehicleService vehicleService = mock(VehicleService.class);
        VehicleTelemetryIdempotencyService idempotencyService = mock(VehicleTelemetryIdempotencyService.class);
        RealFleetAdapter realFleetAdapter = new RealFleetAdapter(
                mock(FleetRuntimeService.class),
                mock(FleetTelemetryPersistenceService.class),
                noopSwapCoordinator());
        FleetAdapterRegistry registry = new FleetAdapterRegistry(java.util.List.of(realFleetAdapter));
        VehicleGatewayServiceImpl gatewayService = new VehicleGatewayServiceImpl(
                vehicleService,
                mock(VehicleReportService.class),
                registry,
                idempotencyService);

        VehicleEntity vehicle = realVehicle();
        vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        when(idempotencyService.markIfFirstTelemetry(any())).thenReturn(true);
        when(vehicleService.getByVehicleCode("REAL-001")).thenReturn(vehicle);

        assertThrows(BusinessException.class, () -> gatewayService.ingestTelemetry(telemetryRequest(3003L)));
    }

    private VehicleEntity realVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(1L);
        vehicle.setVehicleCode("REAL-001");
        vehicle.setLinkMode(VehicleLinkMode.REAL.name());
        vehicle.setOnlineStatus("ONLINE");
        vehicle.setDispatchStatus("IDLE");
        return vehicle;
    }

    private VehicleTelemetryRequest telemetryRequest(long eventSeq) {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setVehicleCode("REAL-001");
        request.setRuntimeStage("STANDBY");
        request.setPluggedIn(false);
        request.setSoc(95);
        request.setX(BigDecimal.valueOf(220));
        request.setY(BigDecimal.valueOf(170));
        request.setReportTime(LocalDateTime.now());
        request.setEventSeq(eventSeq);
        return request;
    }
}
