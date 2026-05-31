package com.fsd.dispatch.fleet.real;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapter;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.geo.FleetGeoResolver;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.service.FleetTelemetryPersistenceService;
import com.fsd.dispatch.service.GeofenceBreachService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RealFleetAdapter implements FleetAdapter {

    private final FleetRuntimeService fleetRuntimeService;
    private final FleetTelemetryPersistenceService telemetryPersistenceService;
    private final RealFleetSwapCoordinator swapCoordinator;
    private final FleetGeoResolver fleetGeoResolver;
    private final GeofenceBreachService geofenceBreachService;

    @org.springframework.beans.factory.annotation.Value("${fsd.automation.default-park-id:1}")
    private Long defaultParkId;

    public RealFleetAdapter(FleetRuntimeService fleetRuntimeService,
                            FleetTelemetryPersistenceService telemetryPersistenceService,
                            RealFleetSwapCoordinator swapCoordinator,
                            FleetGeoResolver fleetGeoResolver,
                            GeofenceBreachService geofenceBreachService) {
        this.fleetRuntimeService = fleetRuntimeService;
        this.telemetryPersistenceService = telemetryPersistenceService;
        this.swapCoordinator = swapCoordinator;
        this.fleetGeoResolver = fleetGeoResolver;
        this.geofenceBreachService = geofenceBreachService;
    }

    @Override
    public VehicleLinkMode supportedLinkMode() {
        return VehicleLinkMode.REAL;
    }

    public void ingestTelemetry(VehicleEntity vehicle, VehicleTelemetryRequest request) {
        FleetRuntime existing = fleetRuntimeService.get(vehicle.getId()).orElse(null);
        swapCoordinator.onTelemetry(vehicle, request, existing);
        List<FleetTrajectoryPoint> trajectory = appendTrajectory(existing, request);
        GeoPoint geo = fleetGeoResolver.resolve(request).orElse(null);
        FleetRuntime runtime = FleetRuntime.builder()
                .vehicleId(vehicle.getId())
                .runtimeStage(request.getRuntimeStage())
                .pluggedIn(request.getPluggedIn())
                .targetCode(request.getTargetCode())
                .targetType(request.getTargetType())
                .soc(request.getSoc())
                .x(request.getX())
                .y(request.getY())
                .longitude(geo != null ? geo.longitude() : null)
                .latitude(geo != null ? geo.latitude() : null)
                .lastTelemetryAt(request.getReportTime())
                .trajectory(trajectory)
                .build();
        fleetRuntimeService.save(runtime);
        if (geo != null) {
            geofenceBreachService.evaluateVehiclePosition(defaultParkId, vehicle, geo.longitude(), geo.latitude());
        }
        if (request.getX() != null && request.getY() != null) {
            telemetryPersistenceService.persistPoint(
                    vehicle.getId(),
                    null,
                    request.getX(),
                    request.getY(),
                    request.getSoc(),
                    request.getReportTime());
        }
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
