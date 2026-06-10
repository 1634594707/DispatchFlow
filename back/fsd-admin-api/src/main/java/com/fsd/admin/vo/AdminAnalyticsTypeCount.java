package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsTypeCount {

    private String type;

    private long count;

    private double ratio;
}
