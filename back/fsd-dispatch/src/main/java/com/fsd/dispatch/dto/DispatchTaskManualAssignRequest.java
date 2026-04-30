package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchTaskManualAssignRequest {

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    @NotBlank(message = "operatorId is required")
    private String operatorId;

    @NotBlank(message = "operatorName is required")
    private String operatorName;

    private String remark;
}
