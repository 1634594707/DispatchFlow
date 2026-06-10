package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsDailySummaryResponse {

    private String date;

    private long orderTotal;

    private long orderCompleted;

    private double orderCompletionRate;

    private long taskTotal;

    private long taskSuccess;

    private long openExceptionCount;

    private long resolvedExceptionCount;

    private double dayOverDayOrderRate;

    private double weekOverWeekOrderRate;

    private List<String> highlightEvents;
}
