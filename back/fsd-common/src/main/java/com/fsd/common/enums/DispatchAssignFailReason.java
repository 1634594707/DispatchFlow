package com.fsd.common.enums;

/**
 * Structured auto-assign failure codes (P2-07).
 */
public enum DispatchAssignFailReason {
    NO_VEHICLE,
    LOW_SOC,
    /**
     * SOC 够、但因维保/车型/车队池/配送区/载重等约束全部不满足而无车可派（§7.2）。
     *
     * <p>以前这些过滤器和 SOC 混在同一层，任何一条不满足都对外报 {@link #LOW_SOC}，
     * 于是"派单失败原因分布"里的低电量占比把别的成因全吞了 —— 标签不可信，读数就不能用。
     */
    NO_MATCHING_VEHICLE,
    UNREACHABLE,
    HUB_CAPACITY_FULL,
    ROUTE_OCCUPANCY_FULL,
    CONFLICT,
    /** 遥测过期或缺失：数据年龄超过统一阈值，禁止派车（路线图 5.1）。 */
    TELEMETRY_STALE
}
