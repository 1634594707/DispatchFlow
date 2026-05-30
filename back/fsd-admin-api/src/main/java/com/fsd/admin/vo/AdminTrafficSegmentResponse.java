package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrafficSegmentResponse {

    private Long segmentId;

    private String fromNodeCode;

    private String toNodeCode;

    private String status;

    private Integer speedLimitKmh;

    private Integer congestionLevel;

    private int nearbyVehicleCount;
}
