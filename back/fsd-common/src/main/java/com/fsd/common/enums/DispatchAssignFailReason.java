package com.fsd.common.enums;

/**
 * Structured auto-assign failure codes (P2-07).
 */
public enum DispatchAssignFailReason {
    NO_VEHICLE,
    LOW_SOC,
    UNREACHABLE,
    HUB_CAPACITY_FULL,
    ROUTE_OCCUPANCY_FULL,
    CONFLICT
}
