package com.fsd.dispatch.fleet.service;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FleetSnapshotAssembler {

    private final FleetChargePolicy fleetChargePolicy;

    public FleetSnapshotAssembler(FleetChargePolicy fleetChargePolicy) {
        this.fleetChargePolicy = fleetChargePolicy;
    }

    public ParkVehicleSnapshotResponse assemble(VehicleEntity vehicle, FleetRuntime runtime) {
        FleetRuntime effectiveRuntime = runtime != null ? runtime : defaultRuntime(vehicle);
        return ParkVehicleSnapshotResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleCode(vehicle.getVehicleCode())
                .vehicleName(vehicle.getVehicleName())
                .onlineStatus(vehicle.getOnlineStatus())
                .dispatchStatus(vehicle.getDispatchStatus())
                .currentTaskId(vehicle.getCurrentTaskId())
                .currentOrderId(vehicle.getCurrentOrderId())
                .batteryLevel(vehicle.getBatteryLevel())
                .x(firstNonNull(effectiveRuntime.getX(), vehicle.getCurrentLongitude()))
                .y(firstNonNull(effectiveRuntime.getY(), vehicle.getCurrentLatitude()))
                .runtimeStage(effectiveRuntime.getRuntimeStage())
                .targetCode(effectiveRuntime.getTargetCode())
                .targetType(effectiveRuntime.getTargetType())
                .charging(fleetChargePolicy.isActivelyCharging(effectiveRuntime.getRuntimeStage()))
                .lowBattery(fleetChargePolicy.isLowSoc(vehicle.getBatteryLevel()))
                .linkMode(resolveLinkMode(vehicle))
                .trajectory(toParkPoints(effectiveRuntime.getTrajectory()))
                .build();
    }

    public FleetRuntime defaultRuntime(VehicleEntity vehicle) {
        return FleetRuntime.builder()
                .vehicleId(vehicle.getId())
                .runtimeStage("STANDBY")
                .pluggedIn(false)
                .targetType("STANDBY")
                .soc(vehicle.getBatteryLevel())
                .x(vehicle.getCurrentLongitude())
                .y(vehicle.getCurrentLatitude())
                .lastTelemetryAt(LocalDateTime.now())
                .trajectory(new ArrayList<>())
                .build();
    }

    private List<ParkPointResponse> toParkPoints(List<FleetTrajectoryPoint> trajectory) {
        if (trajectory == null || trajectory.isEmpty()) {
            return List.of();
        }
        return trajectory.stream()
                .map(point -> ParkPointResponse.builder()
                        .code(point.getCode())
                        .x(point.getX())
                        .y(point.getY())
                        .build())
                .toList();
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    private static String resolveLinkMode(com.fsd.vehicle.entity.VehicleEntity vehicle) {
        return vehicle.getLinkMode() == null || vehicle.getLinkMode().isBlank()
                ? VehicleLinkMode.SIM.name()
                : vehicle.getLinkMode();
    }
}
