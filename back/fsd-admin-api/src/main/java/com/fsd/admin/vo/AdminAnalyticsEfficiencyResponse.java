package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsEfficiencyResponse {

    private String period;

    private List<AdminAnalyticsTrendPoint> orderCompletionTrend;

    private double avgTaskDurationMinutes;

    private double vehicleUtilizationRate;

    private List<AdminAnalyticsHourlyPoint> peakHours;

    /** P1-1：调度指标块。口径见 AnalyticsAdminServiceImpl#buildDispatchMetrics 的注释。 */
    private DispatchMetrics dispatchMetrics;

    @Data
    @Builder
    public static class DispatchMetrics {

        /** 在线 && IDLE && SOC ≥ 最低可派线 */
        private long availableVehicles;

        /** dispatchStatus=BUSY */
        private long busyVehicles;

        /** 运行态 ∈ {TO_CHARGING, CHARGING, WAIT_CHARGING}（8 态中的补能三态） */
        private long chargingVehicles;

        /** 运行态含 MANUAL（手动接管） */
        private long manualPendingVehicles;

        /** 窗口内 status ∈ {CREATED, WAITING_DISPATCH}（未派 backlog） */
        private long pendingOrders;

        /** availableVehicles / max(pendingOrders, 1) */
        private double supplyDemandRatio;

        /** 在线 && SOC &lt; 低电告警线（低 SOC 运力损失代理） */
        private long lowSocVehicles;
    }
}
