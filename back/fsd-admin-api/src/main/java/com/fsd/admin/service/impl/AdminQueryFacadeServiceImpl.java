package com.fsd.admin.service.impl;

import com.fsd.admin.dto.AdminExceptionQueryRequest;
import com.fsd.admin.dto.AdminOrderQueryRequest;
import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.admin.dto.AdminVehicleQueryRequest;
import com.fsd.admin.service.AdminQueryFacadeService;
import com.fsd.common.model.PageResponse;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.order.service.OrderAdminQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class AdminQueryFacadeServiceImpl implements AdminQueryFacadeService {

    private final OrderAdminQueryService orderAdminQueryService;
    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final VehicleAdminQueryService vehicleAdminQueryService;

    public AdminQueryFacadeServiceImpl(OrderAdminQueryService orderAdminQueryService,
                                       DispatchAdminQueryService dispatchAdminQueryService,
                                       VehicleAdminQueryService vehicleAdminQueryService) {
        this.orderAdminQueryService = orderAdminQueryService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.vehicleAdminQueryService = vehicleAdminQueryService;
    }

    @Override
    public PageResponse<OrderAdminListItemResponse> queryOrders(AdminOrderQueryRequest request) {
        List<OrderAdminListItemResponse> filtered = orderAdminQueryService.listOrders().stream()
                .filter(matchOrder(request))
                .toList();
        return paginate(filtered, request.getPageNo(), request.getPageSize());
    }

    @Override
    public PageResponse<DispatchTaskListItemResponse> queryTasks(AdminTaskQueryRequest request) {
        List<DispatchTaskListItemResponse> filtered = dispatchAdminQueryService.listTasks().stream()
                .filter(matchTask(request))
                .toList();
        return paginate(filtered, request.getPageNo(), request.getPageSize());
    }

    @Override
    public PageResponse<DispatchExceptionRecordEntity> queryExceptions(AdminExceptionQueryRequest request) {
        List<DispatchExceptionRecordEntity> filtered = dispatchAdminQueryService.listExceptions().stream()
                .filter(matchException(request))
                .toList();
        return paginate(filtered, request.getPageNo(), request.getPageSize());
    }

    @Override
    public PageResponse<VehicleAdminListItemResponse> queryVehicles(AdminVehicleQueryRequest request) {
        List<VehicleAdminListItemResponse> filtered = vehicleAdminQueryService.listVehicles().stream()
                .filter(matchVehicle(request))
                .toList();
        return paginate(filtered, request.getPageNo(), request.getPageSize());
    }

    private Predicate<OrderAdminListItemResponse> matchOrder(AdminOrderQueryRequest request) {
        return order -> contains(order.getOrderNo(), request.getOrderNo())
                && contains(order.getExternalOrderNo(), request.getExternalOrderNo())
                && equalsValue(order.getStatus(), request.getStatus())
                && equalsValue(order.getPriority(), request.getPriority());
    }

    private Predicate<DispatchTaskListItemResponse> matchTask(AdminTaskQueryRequest request) {
        return task -> contains(task.getTaskNo(), request.getTaskNo())
                && equalsLong(task.getOrderId(), request.getOrderId())
                && equalsLong(task.getVehicleId(), request.getVehicleId())
                && equalsValue(task.getStatus(), request.getStatus());
    }

    private Predicate<DispatchExceptionRecordEntity> matchException(AdminExceptionQueryRequest request) {
        return exception -> equalsValue(exception.getExceptionType(), request.getExceptionType())
                && equalsValue(exception.getExceptionStatus(), request.getExceptionStatus())
                && equalsLong(exception.getOrderId(), request.getOrderId())
                && equalsLong(exception.getVehicleId(), request.getVehicleId());
    }

    private Predicate<VehicleAdminListItemResponse> matchVehicle(AdminVehicleQueryRequest request) {
        return vehicle -> contains(vehicle.getVehicleCode(), request.getVehicleCode())
                && equalsValue(vehicle.getOnlineStatus(), request.getOnlineStatus())
                && equalsValue(vehicle.getDispatchStatus(), request.getDispatchStatus());
    }

    private <T> PageResponse<T> paginate(List<T> records, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, records.size());
        int toIndex = Math.min(fromIndex + safePageSize, records.size());
        return PageResponse.<T>builder()
                .total(records.size())
                .pageNo(safePageNo)
                .pageSize(safePageSize)
                .records(records.subList(fromIndex, toIndex))
                .build();
    }

    private boolean contains(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.contains(expected));
    }

    private boolean equalsValue(String actual, String expected) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean equalsLong(Long actual, Long expected) {
        return expected == null || expected.equals(actual);
    }
}
