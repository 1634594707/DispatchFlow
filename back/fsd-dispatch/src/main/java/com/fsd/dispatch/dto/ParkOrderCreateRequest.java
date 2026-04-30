package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ParkOrderCreateRequest {

    private String externalOrderNo;

    @NotNull(message = "pickupStationId is required")
    private Long pickupStationId;

    @NotNull(message = "dropoffStationId is required")
    private Long dropoffStationId;

    private String priority;

    private String remark;
}
