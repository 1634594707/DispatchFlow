package com.fsd.dispatch.geo;

import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves GCJ-02 coordinates for fleet telemetry: prefer explicit lat/lng from device,
 * otherwise derive from park schematic x/y.
 */
@Component
public class FleetGeoResolver {

    private final ParkGeoTransformService parkGeoTransformService;

    public FleetGeoResolver(ParkGeoTransformService parkGeoTransformService) {
        this.parkGeoTransformService = parkGeoTransformService;
    }

    public Optional<GeoPoint> resolve(VehicleTelemetryRequest request) {
        return resolve(request.getX(), request.getY(), request.getLongitude(), request.getLatitude());
    }

    public Optional<GeoPoint> resolve(BigDecimal parkX, BigDecimal parkY,
                                      BigDecimal longitude, BigDecimal latitude) {
        if (longitude != null && latitude != null) {
            return Optional.of(new GeoPoint(longitude, latitude));
        }
        return parkGeoTransformService.toGcj02(parkX, parkY);
    }
}
