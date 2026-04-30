package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkRoadSegmentResponse {

    private String from;

    private String to;
}
