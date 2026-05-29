package com.fsd.dispatch.fleet.real;

import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RealFleetAdapter {

    private final FleetRuntimeService fleetRuntimeService;

    public RealFleetAdapter(FleetRuntimeService fleetRuntimeService) {
        this.fleetRuntimeService = fleetRuntimeService;
    }

    public void ingestTelemetry(VehicleEntity vehicle, VehicleTelemetryRequest request) {
        FleetRuntime existing = fleetRuntimeService.get(vehicle.getId()).orElse(null);
        List<FleetTrajectoryPoint> trajectory = appendTrajectory(existing, request);
        FleetRuntime runtime = FleetRuntime.builder()
                .vehicleId(vehicle.getId())
                .runtimeStage(request.getRuntimeStage())
                .pluggedIn(request.getPluggedIn())
                .targetCode(request.getTargetCode())
                .targetType(request.getTargetType())
                .soc(request.getSoc())
                .x(request.getX())
                .y(request.getY())
                .lastTelemetryAt(request.getReportTime())
                .trajectory(trajectory)
                .build();
        fleetRuntimeService.save(runtime);
    }

    private List<FleetTrajectoryPoint> appendTrajectory(FleetRuntime existing, VehicleTelemetryRequest request) {
        List<FleetTrajectoryPoint> trajectory = existing != null && existing.getTrajectory() != null
                ? new ArrayList<>(existing.getTrajectory())
                : new ArrayList<>();
        String pointCode = Optional.ofNullable(request.getTargetCode()).orElse(request.getRuntimeStage());
        FleetTrajectoryPoint latest = trajectory.isEmpty() ? null : trajectory.get(trajectory.size() - 1);
        if (latest == null
                || latest.getX() == null
                || latest.getY() == null
                || latest.getX().compareTo(request.getX()) != 0
                || latest.getY().compareTo(request.getY()) != 0) {
            trajectory.add(FleetTrajectoryPoint.builder()
                    .code(pointCode)
                    .x(request.getX())
                    .y(request.getY())
                    .build());
        }
        int maxTrail = 30;
        if (trajectory.size() > maxTrail) {
            return new ArrayList<>(trajectory.subList(trajectory.size() - maxTrail, trajectory.size()));
        }
        return trajectory;
    }
}
