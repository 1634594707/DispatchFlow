package com.fsd.dispatch.geo;

/**
 * Granular unreachable reason codes (P1-4).
 *
 * Used by {@link RoadRouteService}, {@link com.fsd.dispatch.service.ParkRoutePlannerService}
 * and the dispatch vehicle-assign flow to return *why* a route is not feasible,
 * rather than a generic "route failed" message.
 */
public enum RouteUnreachableReason {

    /** Road network is empty — no nodes/segments for this park. */
    ROAD_NETWORK_EMPTY,

    /** No path exists on the graph between the start and end nodes. */
    NO_PATH_ON_GRAPH,

    /** Start point cannot be snapped to any road node within snap threshold. */
    START_OFF_ROAD,

    /** End point cannot be snapped to any road node within snap threshold. */
    END_OFF_ROAD,

    /** A road segment along the path crosses a building polygon (P0-4). */
    CROSSES_BUILDING,

    /** A road segment along the path crosses a river / forbidden service yard. */
    CROSSES_RIVER,

    /** Vehicle width exceeds road usable width (P1-3). */
    VEHICLE_TOO_WIDE_FOR_ROAD,

    /** Vehicle type is not in the road segment's allowed_vehicle_types list. */
    VEHICLE_TYPE_NOT_ALLOWED_ON_ROAD,

    /** Vehicle allowed_road_classes does not include this segment's road_class. */
    ROAD_CLASS_NOT_ALLOWED_FOR_VEHICLE,

    /** Road segment is temporarily blocked (within blocked_from..blocked_until window). */
    ROAD_TEMPORARILY_BLOCKED,

    /** Gate on the path is closed (RESTRICTED access_state without permission). */
    GATE_CLOSED,

    /** Target station has no service position configured (P0-5). */
    NO_SERVICE_POSITION_CONFIGURED,

    /** Target station is in MAINTENANCE / OUT_OF_SERVICE status. */
    STATION_OUT_OF_SERVICE,

    /** All service positions at the target station are occupied (P1-10). */
    SERVICE_POSITION_OCCUPIED,

    /** Vehicle SOC is insufficient to reach destination via this route. */
    INSUFFICIENT_SOC;

    public String code() {
        return name();
    }
}
