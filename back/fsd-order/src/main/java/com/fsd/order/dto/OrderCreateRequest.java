package com.fsd.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateRequest {

    @NotBlank(message = "externalOrderNo is required")
    private String externalOrderNo;

    @NotBlank(message = "sourceType is required")
    private String sourceType;

    @NotBlank(message = "bizType is required")
    private String bizType;

    @NotNull(message = "pickupPointId is required")
    private Long pickupPointId;

    @NotNull(message = "dropoffPointId is required")
    private Long dropoffPointId;

    @NotBlank(message = "priority is required")
    private String priority;

    private String remark;
}
