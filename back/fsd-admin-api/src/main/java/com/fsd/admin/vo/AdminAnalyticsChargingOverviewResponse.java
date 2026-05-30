package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsChargingOverviewResponse {

    private List<AdminAnalyticsChargingSessionItem> activeSessions;

    private long activeSessionCount;

    private long occupiedPileCount;

    private long totalPileCount;

    private double avgChargeSpeedPerHour;

    private List<AdminAnalyticsChargingHistoryItem> recentHistory;
}
