package com.fsd.admin.support;

import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.dispatch.dto.DispatchTaskQueryRequest;

public final class DispatchTaskQueryMapper {

    private DispatchTaskQueryMapper() {
    }

    public static DispatchTaskQueryRequest toDispatchRequest(AdminTaskQueryRequest request) {
        DispatchTaskQueryRequest target = new DispatchTaskQueryRequest();
        target.setTaskNo(request.getTaskNo());
        target.setOrderId(request.getOrderId());
        target.setVehicleId(request.getVehicleId());
        target.setStatus(request.getStatus());
        target.setManualFlag(request.getManualFlag());
        target.setWithOpenExceptionOnly(request.getWithOpenExceptionOnly());
        target.setParkId(request.getParkId());
        target.setPoolStatus(request.getPoolStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }
}
