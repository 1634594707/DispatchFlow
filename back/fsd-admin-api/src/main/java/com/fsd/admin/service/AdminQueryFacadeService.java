package com.fsd.admin.service;

import com.fsd.admin.dto.AdminExceptionQueryRequest;
import com.fsd.admin.dto.AdminOrderQueryRequest;
import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.admin.dto.AdminVehicleQueryRequest;
import com.fsd.common.model.PageResponse;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;

public interface AdminQueryFacadeService {

    PageResponse<OrderAdminListItemResponse> queryOrders(AdminOrderQueryRequest request);

    PageResponse<DispatchTaskListItemResponse> queryTasks(AdminTaskQueryRequest request);

    PageResponse<DispatchExceptionRecordEntity> queryExceptions(AdminExceptionQueryRequest request);

    PageResponse<VehicleAdminListItemResponse> queryVehicles(AdminVehicleQueryRequest request);
}
