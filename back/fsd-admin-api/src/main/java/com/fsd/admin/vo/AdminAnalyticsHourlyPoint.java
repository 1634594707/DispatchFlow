package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsHourlyPoint {

    private int hour;

    private long orderCount;

    private long taskCount;
}
