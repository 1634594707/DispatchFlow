package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTaskManualAssignRequest {

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    private String remark;
}
