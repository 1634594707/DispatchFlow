package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DispatchExceptionResolveRequest {

    @NotBlank(message = "resolverId is required")
    private String resolverId;

    @NotBlank(message = "resolverName is required")
    private String resolverName;

    @NotBlank(message = "action is required")
    private String action;

    private String remark;
}
