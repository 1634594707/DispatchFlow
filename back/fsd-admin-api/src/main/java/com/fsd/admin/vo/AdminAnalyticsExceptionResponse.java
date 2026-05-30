package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsExceptionResponse {

    private String period;

    private List<AdminAnalyticsTypeCount> typeDistribution;

    private List<AdminAnalyticsTrendPoint> exceptionTrend;

    private double avgResolutionMinutes;

    private List<AdminAnalyticsTypeCount> rootCauseHints;
}
