package com.fsd.dispatch.geo;

import java.time.Duration;
import java.util.List;

/**
 * Route metrics calculated after path planning (P1-5).
 *
 * Used by the dispatch flow and admin API to surface:
 *   - total length (meters)
 *   - estimated travel time (seconds, based on per-segment speed limits)
 *   - waiting time at service positions (seconds)
 *   - charging time (seconds, if trip includes a charging stop)
 *   - risk points (list of node codes where the route crosses gate / narrow / mixed-traffic zones)
 *   - unreachable reason (when route cannot be planned; P1-4)
 */
public record RouteMetrics(
        double totalLengthMeters,
        long estimatedTravelSeconds,
        long waitingSeconds,
        long chargingSeconds,
        List<String> riskPoints,
        RouteUnreachableReason unreachableReason,
        String unreachableDetail) {

    public static RouteMetrics empty() {
        return new RouteMetrics(0D, 0L, 0L, 0L, List.of(), null, null);
    }

    public static RouteMetrics unreachable(RouteUnreachableReason reason, String detail) {
        return new RouteMetrics(0D, 0L, 0L, 0L, List.of(), reason, detail);
    }

    public boolean isReachable() {
        return unreachableReason == null;
    }

    /** Total trip time including travel + waiting + charging. */
    public Duration totalTripDuration() {
        return Duration.ofSeconds(estimatedTravelSeconds + waitingSeconds + chargingSeconds);
    }
}
