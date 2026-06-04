package com.fsd.dispatch.fleet.simulation;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.FleetAdapter;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.geo.FleetGeoResolver;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.service.GeofenceBreachService;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SimulationFleetAdapter implements FleetAdapter {

    private final FleetRuntimeService fleetRuntimeService;
    private final FleetGeoResolver fleetGeoResolver;
    private final GeofenceBreachService geofenceBreachService;

    @org.springframework.beans.factory.annotation.Value("${fsd.automation.default-park-id:1}")
    private Long defaultParkId;

    public SimulationFleetAdapter(FleetRuntimeService fleetRuntimeService,
                                  FleetGeoResolver fleetGeoResolver,
                                  GeofenceBreachService geofenceBreachService) {
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetGeoResolver = fleetGeoResolver;
        this.geofenceBreachService = geofenceBreachService;
    }

    @Override
    public VehicleLinkMode supportedLinkMode() {
        return VehicleLinkMode.SIM;
    }

    public void publishTelemetry(VehicleEntity vehicle, SimulationMotionState motion) {
        BigDecimal x = vehicle.getCurrentLongitude();
        BigDecimal y = vehicle.getCurrentLatitude();
        GeoPoint geo = resolveGeo(motion, x, y);
        FleetRuntime runtime = FleetRuntime.builder()
                .vehicleId(vehicle.getId())
                .runtimeStage(motion.stage)
                .pluggedIn(motion.pluggedIn)
                .targetCode(motion.targetCode)
                .targetType(motion.targetType)
                .soc(vehicle.getBatteryLevel())
                .x(x)
                .y(y)
                .longitude(geo != null ? geo.longitude() : null)
                .latitude(geo != null ? geo.latitude() : null)
                .heading(motion != null ? Double.valueOf(motion.headingDegrees) : null)
                .lastTelemetryAt(LocalDateTime.now())
                .trajectory(toTrajectory(motion != null ? motion.trail : null))
                .geoTrajectory(toGeoTrajectory(motion))
                .plannedRouteGeo(toPlannedRoute(motion))
                .routeSource(motion != null ? motion.routeSource : null)
                .routeInvalid(motion != null && motion.routeInvalid)
                .build();
        fleetRuntimeService.save(runtime);
        if (geo != null) {
            geofenceBreachService.evaluateVehiclePosition(defaultParkId, vehicle, geo.longitude(), geo.latitude());
        }
    }

    private GeoPoint resolveGeo(SimulationMotionState motion, BigDecimal parkX, BigDecimal parkY) {
        if (motion != null && motion.geoLongitude != null && motion.geoLatitude != null) {
            return new GeoPoint(motion.geoLongitude, motion.geoLatitude);
        }
        return fleetGeoResolver.resolve(parkX, parkY, null, null).orElse(null);
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
                    .longitude(point.getLongitude())
                    .latitude(point.getLatitude())
                    .build());
        }
        return points;
    }

    private List<FleetTrajectoryPoint> toGeoTrajectory(SimulationMotionState motion) {
        List<FleetTrajectoryPoint> points = new ArrayList<>();
        if (motion == null || motion.geoTrail.isEmpty()) {
            return points;
        }
        for (GeoPoint point : motion.geoTrail) {
            points.add(FleetTrajectoryPoint.builder()
                    .longitude(point.longitude())
                    .latitude(point.latitude())
                    .build());
        }
        return points;
    }

    private List<FleetTrajectoryPoint> toPlannedRoute(SimulationMotionState motion) {
        List<FleetTrajectoryPoint> points = new ArrayList<>();
        if (motion == null || motion.plannedGeoPolyline == null) {
            return points;
        }
        for (GeoPoint point : motion.plannedGeoPolyline) {
            points.add(FleetTrajectoryPoint.builder()
                    .longitude(point.longitude())
                    .latitude(point.latitude())
                    .build());
        }
        return points;
    }
}
