package com.fsd.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminDigitalTwinSimulateRequest {

    private Long parkId;

    @NotBlank
    private String scenario;

    @Min(1)
    private Integer pendingTaskCount;
}
