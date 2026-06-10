package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrafficSummaryResponse {

    private Long parkId;

    private Integer maxCongestionLevel;

    private Integer highCongestionSegmentCount;

    private Integer pausedZoneCount;

    private Integer disabledSegmentCount;
}
