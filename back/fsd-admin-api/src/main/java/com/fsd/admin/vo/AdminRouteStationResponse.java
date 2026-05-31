package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminRouteStationResponse {

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String stationType;

    private Integer sequenceNo;
}
