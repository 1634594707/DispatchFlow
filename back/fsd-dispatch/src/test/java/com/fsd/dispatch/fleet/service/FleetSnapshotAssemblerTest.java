package com.fsd.dispatch.fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FleetSnapshotAssemblerTest {

    private final FleetSnapshotAssembler assembler = new FleetSnapshotAssembler(
            mock(FleetChargePolicy.class),
            new ParkGeoTransformService(new ParkPilotProperties()));

    @Test
    void shouldPreferRedisGeoOverParkTransform() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(1L);
        vehicle.setVehicleCode("REAL-001");
        vehicle.setOnlineStatus("ONLINE");
        vehicle.setDispatchStatus("IDLE");
        vehicle.setBatteryLevel(80);

        FleetRuntime runtime = FleetRuntime.builder()
                .vehicleId(1L)
                .runtimeStage("STANDBY")
                .x(BigDecimal.valueOf(100))
                .y(BigDecimal.valueOf(200))
                .longitude(new BigDecimal("121.070000"))
                .latitude(new BigDecimal("31.920000"))
                .lastTelemetryAt(LocalDateTime.now())
                .build();

        var snapshot = assembler.assemble(vehicle, runtime);

        assertEquals(new BigDecimal("121.070000"), snapshot.getLongitude());
        assertEquals(new BigDecimal("31.920000"), snapshot.getLatitude());
        assertEquals(false, snapshot.getTelemetryStale());
    }

    @Test
    void shouldDeriveGeoFromParkXYWhenRedisGeoMissing() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(2L);
        vehicle.setVehicleCode("PARK-001");
        vehicle.setOnlineStatus("ONLINE");
        vehicle.setDispatchStatus("IDLE");
        vehicle.setBatteryLevel(90);
        vehicle.setCurrentLongitude(BigDecimal.valueOf(600));
        vehicle.setCurrentLatitude(BigDecimal.valueOf(400));

        var snapshot = assembler.assemble(vehicle, null);

        assertEquals(new BigDecimal("121.080354"), snapshot.getLongitude());
        assertEquals(new BigDecimal("31.961977"), snapshot.getLatitude());
        assertTrue(snapshot.getTelemetryStale());
    }
}
