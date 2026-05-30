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
}
