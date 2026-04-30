package com.fsd.admin.controller;

import com.fsd.admin.dto.AdminExceptionQueryRequest;
import com.fsd.admin.dto.AdminOrderQueryRequest;
import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.admin.dto.AdminVehicleQueryRequest;
import com.fsd.admin.dto.AdminDispatchExceptionResolveRequest;
import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.service.AdminQueryFacadeService;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.common.model.PageResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.ParkPilotCommandService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.service.OrderAdminQueryService;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.order.vo.OrderDetailResponse;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDispatchController {

    private final OrderAdminQueryService orderAdminQueryService;
    private final OrderQueryService orderQueryService;
    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final DispatchExceptionService dispatchExceptionService;
    private final AdminDashboardService adminDashboardService;
    private final AdminQueryFacadeService adminQueryFacadeService;
    private final VehicleAdminQueryService vehicleAdminQueryService;
    private final ParkPilotService parkPilotService;
    private final ParkPilotCommandService parkPilotCommandService;

    public AdminDispatchController(OrderAdminQueryService orderAdminQueryService,
                                   OrderQueryService orderQueryService,
                                   DispatchAdminQueryService dispatchAdminQueryService,
                                   DispatchExceptionService dispatchExceptionService,
                                   AdminDashboardService adminDashboardService,
                                   AdminQueryFacadeService adminQueryFacadeService,
                                   VehicleAdminQueryService vehicleAdminQueryService,
                                   ParkPilotService parkPilotService,
                                   ParkPilotCommandService parkPilotCommandService) {
        this.orderAdminQueryService = orderAdminQueryService;
        this.orderQueryService = orderQueryService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.adminDashboardService = adminDashboardService;
        this.adminQueryFacadeService = adminQueryFacadeService;
        this.vehicleAdminQueryService = vehicleAdminQueryService;
        this.parkPilotService = parkPilotService;
        this.parkPilotCommandService = parkPilotCommandService;
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderAdminListItemResponse>> listOrders() {
        return ApiResponse.success(orderAdminQueryService.listOrders());
    }

    @PostMapping("/orders/query")
    public ApiResponse<PageResponse<OrderAdminListItemResponse>> queryOrders(@RequestBody AdminOrderQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryOrders(request));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        return ApiResponse.success(orderQueryService.getOrderDetail(orderId));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<DispatchTaskListItemResponse>> listTasks() {
        return ApiResponse.success(dispatchAdminQueryService.listTasks());
    }

    @PostMapping("/tasks/query")
    public ApiResponse<PageResponse<DispatchTaskListItemResponse>> queryTasks(@RequestBody AdminTaskQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryTasks(request));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DispatchTaskDetailResponse> getTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.success(dispatchAdminQueryService.getTaskDetail(taskId));
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<DispatchExceptionRecordEntity>> listExceptions() {
        return ApiResponse.success(dispatchAdminQueryService.listExceptions());
    }

    @PostMapping("/exceptions/query")
    public ApiResponse<PageResponse<DispatchExceptionRecordEntity>> queryExceptions(@RequestBody AdminExceptionQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryExceptions(request));
    }

    @PostMapping("/exceptions/{exceptionId}/resolve")
    public ApiResponse<Void> resolveException(@PathVariable Long exceptionId,
                                              @Valid @RequestBody AdminDispatchExceptionResolveRequest request) {
        dispatchExceptionService.resolveException(exceptionId, request.toDispatchRequest());
        return ApiResponse.success(null);
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleAdminListItemResponse>> listVehicles() {
        return ApiResponse.success(vehicleAdminQueryService.listVehicles());
    }

    @PostMapping("/vehicles/query")
    public ApiResponse<PageResponse<VehicleAdminListItemResponse>> queryVehicles(@RequestBody AdminVehicleQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryVehicles(request));
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ApiResponse<VehicleAdminDetailResponse> getVehicleDetail(@PathVariable Long vehicleId) {
        return ApiResponse.success(vehicleAdminQueryService.getVehicleDetail(vehicleId));
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<AdminDashboardSummaryResponse> getDashboardSummary() {
        return ApiResponse.success(adminDashboardService.getSummary());
    }

    @GetMapping("/park/layout")
    public ApiResponse<ParkLayoutResponse> getParkLayout() {
        return ApiResponse.success(parkPilotService.getLayout());
    }

    @GetMapping("/park/stations")
    public ApiResponse<List<ParkStationResponse>> listParkStations() {
        return ApiResponse.success(parkPilotService.listStations());
    }

    @GetMapping("/park/vehicles")
    public ApiResponse<List<ParkVehicleSnapshotResponse>> listParkVehicles() {
        return ApiResponse.success(parkPilotService.listVehicleSnapshots());
    }

    @GetMapping("/park/orders")
    public ApiResponse<List<ParkOrderSnapshotResponse>> listParkOrders() {
        return ApiResponse.success(parkPilotService.listOrderSnapshots());
    }

    @PostMapping("/park/orders")
    public ApiResponse<ParkOrderCreateResponse> createParkOrder(@Valid @RequestBody ParkOrderCreateRequest request) {
        return ApiResponse.success(parkPilotCommandService.createParkOrder(request));
    }
}
