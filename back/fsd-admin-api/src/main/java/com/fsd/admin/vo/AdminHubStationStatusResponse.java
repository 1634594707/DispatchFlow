package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHubStationStatusResponse {

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String stationType;

    private Integer capacityLimit;

    private Integer occupancy;

    private boolean full;
}
