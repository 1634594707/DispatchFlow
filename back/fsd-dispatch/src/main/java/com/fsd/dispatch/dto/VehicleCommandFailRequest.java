package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VehicleCommandFailRequest {

    @NotBlank(message = "reason is required")
    private String reason;
}
