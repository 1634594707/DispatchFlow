package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsChainKpiResponse {

    private String period;

    private Long parkId;

    private Double avgCompletionMinutes;

    private Double waitP50Minutes;

    private Double waitP90Minutes;

    private Double tasksPerVehiclePerDay;
}
