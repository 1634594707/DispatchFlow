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

    public boolean ingestTelemetry(VehicleEntity vehicle, VehicleTelemetryRequest request) {
        FleetRuntime existing = fleetRuntimeService.get(vehicle.getId()).orElse(null);
        if (isDelayed(existing, request)) {
            return false;
        }
        swapCoordinator.onTelemetry(vehicle, request, existing);
        List<FleetTrajectoryPoint> trajectory = appendTrajectory(existing, request);
        GeoPoint geo = fleetGeoResolver.resolve(request).orElse(null);
        List<FleetTrajectoryPoint> geoTrajectory = appendGeoTrajectory(existing, geo, request);
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
                .heading(existing != null ? existing.getHeading() : null)
                .lastTelemetryAt(request.getReportTime())
                .lastEventSeq(request.getEventSeq())
                .trajectory(trajectory)
                .geoTrajectory(geoTrajectory)
                .plannedRouteGeo(existing != null ? existing.getPlannedRouteGeo() : new ArrayList<>())
                .routeSource(existing != null ? existing.getRouteSource() : null)
                .routeInvalid(existing != null ? existing.getRouteInvalid() : null)
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
        return true;
    }

    private boolean isDelayed(FleetRuntime existing, VehicleTelemetryRequest request) {
        if (existing == null || existing.getLastTelemetryAt() == null) {
            return false;
        }
        int timeOrder = request.getReportTime().compareTo(existing.getLastTelemetryAt());
        if (timeOrder < 0) {
            return true;
        }
        return timeOrder == 0
                && existing.getLastEventSeq() != null
                && request.getEventSeq() <= existing.getLastEventSeq();
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

    private List<FleetTrajectoryPoint> appendGeoTrajectory(FleetRuntime existing,
                                                            GeoPoint geo,
                                                            VehicleTelemetryRequest request) {
        List<FleetTrajectoryPoint> points = existing != null && existing.getGeoTrajectory() != null
                ? new ArrayList<>(existing.getGeoTrajectory())
                : new ArrayList<>();
        if (geo == null) {
            return points;
        }
        FleetTrajectoryPoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        if (latest == null
                || latest.getLongitude() == null
                || latest.getLatitude() == null
                || latest.getLongitude().compareTo(geo.longitude()) != 0
                || latest.getLatitude().compareTo(geo.latitude()) != 0) {
            points.add(FleetTrajectoryPoint.builder()
                    .code(Optional.ofNullable(request.getTargetCode()).orElse(request.getRuntimeStage()))
                    .longitude(geo.longitude())
                    .latitude(geo.latitude())
                    .build());
        }
        int maxTrail = 30;
        if (points.size() > maxTrail) {
            return new ArrayList<>(points.subList(points.size() - maxTrail, points.size()));
        }
        return points;
    }
}
