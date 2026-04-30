package com.fsd.dispatch.service;

import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;

public interface DispatchExceptionService {

    void recordException(Long taskId, Long orderId, Long vehicleId, String exceptionType, String exceptionMsg);

    void resolveException(Long exceptionId, DispatchExceptionResolveRequest request);

    java.util.List<DispatchExceptionRecordEntity> listOpenExceptions();
}
