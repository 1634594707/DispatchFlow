package com.fsd.admin.controller;

import com.fsd.admin.dto.AdminExceptionQueryRequest;
import com.fsd.admin.dto.AdminOrderQueryRequest;
import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.admin.dto.AdminBatchTaskRequest;
import com.fsd.admin.dto.AdminTaskCancelRequest;
import com.fsd.admin.dto.AdminTaskManualAssignRequest;
import com.fsd.admin.dto.AdminVehicleQueryRequest;
import com.fsd.admin.service.BatchTaskAdminService;
import com.fsd.admin.service.TaskPriorityAdminService;
import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminDispatchExceptionResolveRequest;
import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.service.AdminQueryFacadeService;
import com.fsd.admin.vo.AdminBatchTaskResultResponse;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.common.model.PageResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchWorkbenchResponse;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.service.MobileOrderAuthService;
import com.fsd.dispatch.service.ParkPilotCommandService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.geo.CoordinateTransformService;
import com.fsd.dispatch.vo.GeoCalibrationPointResponse;
import com.fsd.dispatch.vo.GeoTransformResponse;
import com.fsd.dispatch.vo.ParkGeofenceResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOverviewResponse;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.service.OrderAdminQueryService;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.order.vo.OrderDetailResponse;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Dispatch Admin", description = "Dashboard, workbench, task pool, park pilot, and order queries")
public class AdminDispatchController {

    private final OrderAdminQueryService orderAdminQueryService;
    private final OrderQueryService orderQueryService;
    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final DispatchTaskService dispatchTaskService;
    private final DispatchExceptionService dispatchExceptionService;
    private final AdminDashboardService adminDashboardService;
    private final AdminQueryFacadeService adminQueryFacadeService;
    private final VehicleAdminQueryService vehicleAdminQueryService;
    private final ParkPilotService parkPilotService;
    private final ParkPilotCommandService parkPilotCommandService;
    private final BatchTaskAdminService batchTaskAdminService;
    private final TaskPriorityAdminService taskPriorityAdminService;
    private final MobileOrderAuthService mobileOrderAuthService;
    private final CoordinateTransformService coordinateTransformService;

    public AdminDispatchController(OrderAdminQueryService orderAdminQueryService,
                                   OrderQueryService orderQueryService,
                                   DispatchAdminQueryService dispatchAdminQueryService,
                                   DispatchTaskService dispatchTaskService,
                                   DispatchExceptionService dispatchExceptionService,
                                   AdminDashboardService adminDashboardService,
                                   AdminQueryFacadeService adminQueryFacadeService,
                                   VehicleAdminQueryService vehicleAdminQueryService,
                                   ParkPilotService parkPilotService,
                                   ParkPilotCommandService parkPilotCommandService,
                                   BatchTaskAdminService batchTaskAdminService,
                                   TaskPriorityAdminService taskPriorityAdminService,
                                   MobileOrderAuthService mobileOrderAuthService,
                                   CoordinateTransformService coordinateTransformService) {
        this.orderAdminQueryService = orderAdminQueryService;
        this.orderQueryService = orderQueryService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.dispatchTaskService = dispatchTaskService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.adminDashboardService = adminDashboardService;
        this.adminQueryFacadeService = adminQueryFacadeService;
        this.vehicleAdminQueryService = vehicleAdminQueryService;
        this.parkPilotService = parkPilotService;
        this.parkPilotCommandService = parkPilotCommandService;
        this.batchTaskAdminService = batchTaskAdminService;
        this.taskPriorityAdminService = taskPriorityAdminService;
        this.mobileOrderAuthService = mobileOrderAuthService;
        this.coordinateTransformService = coordinateTransformService;
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

    @PostMapping("/tasks/{taskId}/auto-assign")
    public ApiResponse<DispatchTaskAssignResponse> autoAssignTask(@PathVariable Long taskId) {
        return ApiResponse.success(dispatchTaskService.autoAssignTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/manual-assign")
    public ApiResponse<DispatchTaskAssignResponse> manualAssignTask(@PathVariable Long taskId,
                                                                    @Valid @RequestBody AdminTaskManualAssignRequest request,
                                                                    HttpServletRequest httpRequest) {
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(request.getVehicleId());
        OperatorIdentity operator = resolveOperator(httpRequest);
        assignRequest.setOperatorId(operator.id());
        assignRequest.setOperatorName(operator.name());
        assignRequest.setRemark(request.getRemark());
        return ApiResponse.success(dispatchTaskService.manualAssignTask(taskId, assignRequest));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<DispatchTaskAssignResponse> cancelTask(@PathVariable Long taskId,
                                                                @RequestBody(required = false) AdminTaskCancelRequest body,
                                                                HttpServletRequest httpRequest) {
        OperatorIdentity operator = resolveOperator(httpRequest);
        String remark = body != null ? body.getRemark() : null;
        return ApiResponse.success(dispatchTaskService.cancelTask(taskId, operator.id(), operator.name(), remark));
    }

    @PostMapping("/tasks/{taskId}/reassign")
    public ApiResponse<DispatchTaskAssignResponse> reassignTask(@PathVariable Long taskId,
                                                                @Valid @RequestBody AdminTaskManualAssignRequest request,
                                                                HttpServletRequest httpRequest) {
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(request.getVehicleId());
        OperatorIdentity operator = resolveOperator(httpRequest);
        assignRequest.setOperatorId(operator.id());
        assignRequest.setOperatorName(operator.name());
        assignRequest.setRemark(request.getRemark());
        return ApiResponse.success(dispatchTaskService.reassignTask(taskId, assignRequest));
    }

    @PostMapping("/tasks/{taskId}/bump-priority")
    public ApiResponse<Void> bumpTaskPriority(@PathVariable Long taskId) {
        taskPriorityAdminService.bumpTaskPriority(taskId);
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/batch/auto-assign")
    public ApiResponse<AdminBatchTaskResultResponse> batchAutoAssign(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                     HttpServletRequest httpRequest) {
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchAutoAssign(request, operator.id(), operator.name()));
    }

    @PostMapping("/tasks/batch/cancel")
    public ApiResponse<AdminBatchTaskResultResponse> batchCancel(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                 HttpServletRequest httpRequest) {
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchCancel(request, operator.id(), operator.name()));
    }

    @PostMapping("/tasks/batch/reassign")
    public ApiResponse<AdminBatchTaskResultResponse> batchReassign(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                   HttpServletRequest httpRequest) {
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchReassign(request, operator.id(), operator.name()));
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<DispatchExceptionListItemResponse>> listExceptions() {
        return ApiResponse.success(dispatchAdminQueryService.listExceptions());
    }

    @PostMapping("/exceptions/query")
    public ApiResponse<PageResponse<DispatchExceptionListItemResponse>> queryExceptions(
            @RequestBody AdminExceptionQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryExceptions(request));
    }

    @GetMapping("/dispatch/intervention-queue")
    public ApiResponse<DispatchInterventionQueueResponse> getInterventionQueue(
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(dispatchAdminQueryService.getInterventionQueue(parkId));
    }

    @PostMapping("/dispatch/task-pool/query")
    @Operation(summary = "Query task pool with server-side pagination")
    public ApiResponse<PageResponse<DispatchTaskListItemResponse>> queryTaskPool(
            @RequestBody AdminTaskQueryRequest request) {
        return ApiResponse.success(adminQueryFacadeService.queryTasks(request));
    }

    @GetMapping("/dispatch/workbench")
    public ApiResponse<DispatchWorkbenchResponse> getDispatchWorkbench(
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(dispatchAdminQueryService.getWorkbench(parkId));
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
    public ApiResponse<AdminDashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(adminDashboardService.getSummary(parkId));
    }

    @GetMapping("/parks")
    public ApiResponse<List<ParkResponse>> listParks() {
        return ApiResponse.success(parkPilotService.listParks());
    }

    @GetMapping("/park/layout")
    public ApiResponse<ParkLayoutResponse> getParkLayout(@RequestParam(required = false) Long parkId) {
        if (parkId == null) {
            return ApiResponse.success(parkPilotService.getLayout());
        }
        return ApiResponse.success(parkPilotService.getLayout(parkId));
    }

    @GetMapping("/park/geo/calibration-points")
    public ApiResponse<List<GeoCalibrationPointResponse>> listGeoCalibrationPoints(
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(coordinateTransformService.listCalibrationPoints(parkId));
    }

    @GetMapping("/park/geo/transform")
    public ApiResponse<GeoTransformResponse> transformGeoCoordinates(
            @RequestParam(required = false) BigDecimal parkX,
            @RequestParam(required = false) BigDecimal parkY,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal latitude) {
        if (parkX != null && parkY != null) {
            return coordinateTransformService.parkToGcj02(parkX, parkY)
                    .map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.failure("GEO_TRANSFORM_DISABLED", "Geo transform is disabled"));
        }
        if (longitude != null && latitude != null) {
            return coordinateTransformService.gcj02ToPark(longitude, latitude)
                    .map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.failure("GEO_TRANSFORM_DISABLED", "Geo transform is disabled"));
        }
        return ApiResponse.failure("GEO_TRANSFORM_INVALID", "Provide parkX/parkY or longitude/latitude");
    }

    @GetMapping("/park/stations")
    public ApiResponse<List<ParkStationResponse>> listParkStations(@RequestParam(required = false) Long parkId) {
        if (parkId == null) {
            return ApiResponse.success(parkPilotService.listStations());
        }
        return ApiResponse.success(parkPilotService.listStations(parkId));
    }

    @GetMapping("/park/vehicles")
    public ApiResponse<List<ParkVehicleSnapshotResponse>> listParkVehicles() {
        return ApiResponse.success(parkPilotService.listVehicleSnapshots());
    }

    @GetMapping("/park/geofences")
    public ApiResponse<List<ParkGeofenceResponse>> listParkGeofences(@RequestParam(required = false) Long parkId) {
        return ApiResponse.success(parkPilotService.listGeofences(parkId));
    }

    @GetMapping("/park/overview")
    public ApiResponse<List<ParkOverviewResponse>> listParkOverview() {
        return ApiResponse.success(parkPilotService.listParkOverview());
    }

    @GetMapping("/park/orders")
    public ApiResponse<List<ParkOrderSnapshotResponse>> listParkOrders() {
        return ApiResponse.success(parkPilotService.listOrderSnapshots());
    }

    @PostMapping("/park/orders")
    public ApiResponse<ParkOrderCreateResponse> createParkOrder(@Valid @RequestBody ParkOrderCreateRequest request,
                                                                  jakarta.servlet.http.HttpServletRequest httpRequest) {
        String mobileKey = httpRequest.getHeader("X-Mobile-Api-Key");
        if (mobileKey == null || mobileKey.isBlank()) {
            mobileKey = httpRequest.getParameter("mobileApiKey");
        }
        mobileOrderAuthService.validateMobileOrderKey(mobileKey);
        return ApiResponse.success(parkPilotCommandService.createParkOrder(request));
    }

    private OperatorIdentity resolveOperator(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.fromRequest(request);
        if (context == null || context.getUsername() == null) {
            return new OperatorIdentity("system", "系统");
        }
        String name = context.getDisplayName() != null ? context.getDisplayName() : context.getUsername();
        return new OperatorIdentity(context.getUsername(), name);
    }

    private record OperatorIdentity(String id, String name) {
    }
}
