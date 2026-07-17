package com.fsd.admin.dto;

import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class AdminDispatchExceptionBatchResolveRequest {

    @NotEmpty(message = "exceptionIds is required")
    private List<Long> exceptionIds;

    @NotBlank(message = "resolverId is required")
    private String resolverId;

    @NotBlank(message = "resolverName is required")
    private String resolverName;

    @NotBlank(message = "action is required")
    private String action;

    private String remark;

    public DispatchExceptionResolveRequest toDispatchRequest() {
        DispatchExceptionResolveRequest request = new DispatchExceptionResolveRequest();
        request.setResolverId(resolverId);
        request.setResolverName(resolverName);
        request.setAction(action);
        request.setRemark(remark);
        return request;
    }
}
