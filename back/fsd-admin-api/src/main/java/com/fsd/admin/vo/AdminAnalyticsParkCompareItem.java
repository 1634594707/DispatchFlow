package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsParkCompareItem {

    private Long parkId;

    private String parkName;

    private long orderCount;

    private long taskSuccessCount;

    private long openExceptionCount;
}
