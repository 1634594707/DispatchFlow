package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsTrendPoint {

    private String label;

    private long totalCount;

    private long completedCount;

    private double completionRate;
}
