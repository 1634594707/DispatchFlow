package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadRouteHealthResponse {

    private boolean amapDriving;
    private boolean localGraph;
    private long amapSuccessCount;
    private long fallbackCount;
    private int localGraphSegments;
    private String detail;
}