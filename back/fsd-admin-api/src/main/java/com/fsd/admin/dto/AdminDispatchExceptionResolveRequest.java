package com.fsd.admin.dto;

import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminDispatchExceptionResolveRequest {

    @NotBlank(message = "resolverId is required")
    private String resolverId;

    @NotBlank(message = "resolverName is required")
    private String resolverName;

    @NotBlank(message = "action is required")
    private String action;

    private String remark;

    private Long vehicleId;

    public DispatchExceptionResolveRequest toDispatchRequest() {
        DispatchExceptionResolveRequest request = new DispatchExceptionResolveRequest();
        request.setResolverId(resolverId);
        request.setResolverName(resolverName);
        request.setAction(action);
        request.setRemark(remark);
        request.setVehicleId(vehicleId);
        return request;
    }
}
