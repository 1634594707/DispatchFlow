package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminDispatchPauseRequest {

    private Long parkId;

    @NotNull(message = "paused is required")
    private Boolean paused;
}
