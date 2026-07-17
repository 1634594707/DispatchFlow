package com.fsd.admin.dto;

import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Batch resolve request for dispatch exceptions. Extends the single-resolve
 * request and adds the list of exception IDs to resolve in a single
 * transactional operation.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminDispatchExceptionBatchResolveRequest extends AdminDispatchExceptionResolveRequest {

    @NotEmpty(message = "exceptionIds must not be empty")
    private List<Long> exceptionIds;

    @Override
    public DispatchExceptionResolveRequest toDispatchRequest() {
        return super.toDispatchRequest();
    }
}
