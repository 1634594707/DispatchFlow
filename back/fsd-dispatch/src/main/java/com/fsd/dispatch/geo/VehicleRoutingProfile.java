package com.fsd.dispatch.geo;

/**
 * Vehicle routing profile (P1-3) — captures vehicle-specific constraints
 * used to filter the road graph and validate road segments at planning time.
 *
 * Instances are immutable value objects; build one per trip from {@code VehicleEntity}.
 */
public record VehicleRoutingProfile(
        Long vehicleId,
        String vehicleType,
        /** Vehicle width in centimeters (nullable = unknown). */
        Integer widthCm,
        /** Vehicle length in centimeters (nullable = unknown). */
        Integer lengthCm,
        /** Minimum turning radius in meters (nullable = unknown). */
        java.math.BigDecimal turningRadiusM,
        /** Comma-separated allowed road classes (nullable = all allowed). */
        String allowedRoadClasses,
        /** Comma-separated allowed vehicle types when filtering stations (nullable = all). */
        String allowedVehicleTypes,
        /** Safety buffer in meters added around vehicle envelope for collision check. */
        double safetyBufferMeters) {

    /** Default safety buffer (meters) added around the vehicle envelope. */
    public static final double DEFAULT_SAFETY_BUFFER_METERS = 0.5;

    public static VehicleRoutingProfile unknown(Long vehicleId) {
        return new VehicleRoutingProfile(vehicleId, null, null, null, null, null, null, DEFAULT_SAFETY_BUFFER_METERS);
    }

    /** Whether the vehicle is allowed on a road segment with the given allowedVehicleTypes list. */
    public boolean isVehicleTypeAllowed(String segmentAllowedTypes) {
        if (segmentAllowedTypes == null || segmentAllowedTypes.isBlank()) {
            return true;
        }
        if (vehicleType == null || vehicleType.isBlank()) {
            return false;
        }
        for (String allowed : segmentAllowedTypes.split(",")) {
            if (allowed != null && allowed.trim().equalsIgnoreCase(vehicleType)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the vehicle is allowed on a road segment with the given road class. */
    public boolean isRoadClassAllowed(String segmentRoadClass) {
        if (segmentRoadClass == null || segmentRoadClass.isBlank()) {
            return true;
        }
        if (allowedRoadClasses == null || allowedRoadClasses.isBlank()) {
            return true; // vehicle has no restriction
        }
        for (String allowed : allowedRoadClasses.split(",")) {
            if (allowed != null && allowed.trim().equalsIgnoreCase(segmentRoadClass)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the vehicle fits within the given road width (meters). */
    public boolean fitsRoadWidth(java.math.BigDecimal roadWidthMeters) {
        if (roadWidthMeters == null || widthCm == null) {
            return true; // cannot determine — allow
        }
        double vehicleWidthMeters = widthCm / 100.0 + safetyBufferMeters * 2;
        return vehicleWidthMeters <= roadWidthMeters.doubleValue();
    }
}
