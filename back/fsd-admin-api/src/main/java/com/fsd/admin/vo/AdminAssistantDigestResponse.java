package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * P2-1 只读调度助手：异常摘要日报。
 * 数据复用 {@code getDailySummary}（订单/任务口径）与 {@code getExceptionAnalysis}（异常口径），
 * 助手不另立第二套统计。
 */
@Data
@Builder
public class AdminAssistantDigestResponse {

    private String date;

    private Long parkId;

    private long orderTotal;

    private long orderCompleted;

    private double orderCompletionRate;

    private long taskTotal;

    private long taskSuccess;

    private long openExceptionCount;

    private double avgResolutionMinutes;

    /** 异常类型分布（含占比），按次数降序 */
    private List<AdminAnalyticsTypeCount> exceptionTypes;

    /** 根因提示（异常分析的同源推导） */
    private List<AdminAnalyticsTypeCount> rootCauseHints;
}
