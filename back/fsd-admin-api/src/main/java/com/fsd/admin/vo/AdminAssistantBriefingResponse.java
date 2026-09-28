package com.fsd.admin.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * P2-1 只读调度助手的运维简报。
 *
 * <p>只读边界：本 VO 与产出它的 {@code DispatchAssistantService} 没有任何写路径——
 * 助手可以汇总、解释、生成日报，但派车/重派/阈值修改是确定性操作，永远不进这里。
 */
@Data
@Builder
public class AdminAssistantBriefingResponse {

    private String period;

    private Long parkId;

    /** 车队与 backlog（复用 AnalyticsAdminService 的 dispatchMetrics，同口径） */
    private AdminAnalyticsEfficiencyResponse.DispatchMetrics dispatchMetrics;

    /** 补能预测可用性（ALG-FC）：预测缺失时助手按"纯阈值策略在岗"陈述 */
    private Forecast forecast;

    /** 决策快照近貌（最近 N 条的漏斗均值与影子一致率） */
    private SnapshotStats snapshotStats;

    /** 异常概览（复用 getExceptionAnalysis 的类型分布） */
    private List<AdminAnalyticsTypeCount> exceptionTypes;

    @Data
    @Builder
    public static class Forecast {

        private boolean enabled;

        private boolean anyData;

        private int stationCount;

        private LocalDate forecastDate;
    }

    @Data
    @Builder
    public static class SnapshotStats {

        private int sampled;

        private double meanCandidateTotal;

        private double shadowAgreedRate;

        private int shadowRows;
    }
}
