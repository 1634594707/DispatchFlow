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
    CONFLICT,
    /** 遥测过期或缺失：数据年龄超过统一阈值，禁止派车（路线图 5.1）。 */
    TELEMETRY_STALE
}
