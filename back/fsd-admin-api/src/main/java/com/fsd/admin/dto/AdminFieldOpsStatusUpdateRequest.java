package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminFieldOpsStatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status;

    private String notes;
}
