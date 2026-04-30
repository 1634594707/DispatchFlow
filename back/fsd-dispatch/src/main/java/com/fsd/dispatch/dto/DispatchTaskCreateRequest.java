package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchTaskCreateRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotBlank(message = "dispatchType is required")
    private String dispatchType;

    private String remark;
}
