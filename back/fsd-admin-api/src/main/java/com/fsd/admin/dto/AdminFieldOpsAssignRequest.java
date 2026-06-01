package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminFieldOpsAssignRequest {

    @NotNull(message = "assigneeUserId is required")
    private Long assigneeUserId;

    private String notes;
}
