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
    /**
     * 取货点落在"交通管制/暂停区"内，该区域派单被人工挂起。
     *
     * <p>以前这里报的是 {@link #UNREACHABLE}，于是"有人框了一块管制区"和"路网图上不连通"
     * 共用一个码 —— 生产上曾因此把一次排查整条带偏（去查图、查缓存、查锚点，
     * 而真因是 Redis 里一块真相表早已不存在的陈旧管制区）。
     * 与 {@link #NO_MATCHING_VEHICLE} 从 {@link #LOW_SOC} 拆出来是同一个道理：**标签不可信，读数就不能用**。
     */
    ZONE_PAUSED,
    HUB_CAPACITY_FULL,
    ROUTE_OCCUPANCY_FULL,
    CONFLICT,
    /** 遥测过期或缺失：数据年龄超过统一阈值，禁止派车（路线图 5.1）。 */
    TELEMETRY_STALE
}
