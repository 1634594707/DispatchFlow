package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminReportScheduleUpsertRequest {

    private Long id;

    private Long parkId;

    @NotBlank(message = "cronExpression is required")
    private String cronExpression;

    @NotBlank(message = "recipients is required")
    private String recipients;

    private Boolean enabled;
}
