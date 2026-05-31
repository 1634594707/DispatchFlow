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
        GeoPoint geo = fleetGeoResolver.resolve(x, y, null, null).orElse(null);
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
                .lastTelemetryAt(LocalDateTime.now())
                .trajectory(toTrajectory(motion.trail))
                .build();
        fleetRuntimeService.save(runtime);
        if (geo != null) {
            geofenceBreachService.evaluateVehiclePosition(defaultParkId, vehicle, geo.longitude(), geo.latitude());
        }
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
