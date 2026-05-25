package com.fsd.dispatch.fleet.simulation;

import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SimulationFleetAdapter {

    private final FleetRuntimeService fleetRuntimeService;

    public SimulationFleetAdapter(FleetRuntimeService fleetRuntimeService) {
        this.fleetRuntimeService = fleetRuntimeService;
    }

    public void publishTelemetry(VehicleEntity vehicle, SimulationMotionState motion) {
        FleetRuntime runtime = FleetRuntime.builder()
                .vehicleId(vehicle.getId())
                .runtimeStage(motion.stage)
                .pluggedIn(motion.pluggedIn)
                .targetCode(motion.targetCode)
                .targetType(motion.targetType)
                .soc(vehicle.getBatteryLevel())
                .x(vehicle.getCurrentLongitude())
                .y(vehicle.getCurrentLatitude())
                .lastTelemetryAt(LocalDateTime.now())
                .trajectory(toTrajectory(motion.trail))
                .build();
        fleetRuntimeService.save(runtime);
    }

    private List<FleetTrajectoryPoint> toTrajectory(Iterable<ParkPointResponse> trail) {
        List<FleetTrajectoryPoint> points = new ArrayList<>();
        if (trail == null) {
            return points;
        }
        for (ParkPointResponse point : trail) {
            points.add(FleetTrajectoryPoint.builder()
                    .code(point.getCode())
                    .x(point.getX())
                    .y(point.getY())
                    .build());
        }
        return points;
    }
}
