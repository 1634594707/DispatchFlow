package com.fsd.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.fsd.admin.dto.AdminDispatchExceptionBatchResolveRequest;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.service.AdminQueryFacadeService;
import com.fsd.admin.vo.AdminBatchTaskResultResponse;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.model.PageResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchWorkbenchResponse;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
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
import com.fsd.order.service.OrderStateService;
import com.fsd.admin.service.OrderAdminDetailService;
import com.fsd.admin.service.TaskAdminDetailService;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.order.vo.OrderDetailResponse;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "adminToken")
public class AdminDispatchController {

    private final OrderAdminQueryService orderAdminQueryService;
    private final OrderStateService orderStateService;

    private final OrderAdminDetailService orderAdminDetailService;
    private final TaskAdminDetailService taskAdminDetailService;
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
    private final AdminAuthService adminAuthService;
    private final CoordinateTransformService coordinateTransformService;
    private final ParkMapper parkMapper;

    public AdminDispatchController(OrderAdminQueryService orderAdminQueryService,
                                   OrderQueryService orderQueryService,
                                   OrderStateService orderStateService,
                                   OrderAdminDetailService orderAdminDetailService,
                                   TaskAdminDetailService taskAdminDetailService,
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
                                   AdminAuthService adminAuthService,
                                   CoordinateTransformService coordinateTransformService,
                                   ParkMapper parkMapper) {
        this.orderAdminQueryService = orderAdminQueryService;
        this.orderStateService = orderStateService;
        this.orderAdminDetailService = orderAdminDetailService;
        this.taskAdminDetailService = taskAdminDetailService;
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
        this.adminAuthService = adminAuthService;
        this.coordinateTransformService = coordinateTransformService;
        this.parkMapper = parkMapper;
    }

    @GetMapping("/orders")
    @Operation(summary = "List all orders")
    public ApiResponse<List<OrderAdminListItemResponse>> listOrders(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(orderAdminQueryService.listOrders());
    }

    @PostMapping("/orders/query")
    @Operation(summary = "Query orders with pagination and filters")
    public ApiResponse<PageResponse<OrderAdminListItemResponse>> queryOrders(
            @Valid @RequestBody AdminOrderQueryRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminQueryFacadeService.queryOrders(body));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order detail")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(orderAdminDetailService.getEnrichedDetail(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Cancel order")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> cancelOrder(@PathVariable Long orderId,
                                         @RequestBody(required = false) AdminTaskCancelRequest body,
                                         HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        orderStateService.markCancelled(orderId);
        return ApiResponse.success(null);
    }

    @GetMapping("/tasks")
    @Operation(summary = "List all dispatch tasks")
    public ApiResponse<List<DispatchTaskListItemResponse>> listTasks(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAdminQueryService.listTasks());
    }

    @PostMapping("/tasks/query")
    @Operation(summary = "Query tasks with pagination and filters")
    public ApiResponse<PageResponse<DispatchTaskListItemResponse>> queryTasks(
            @Valid @RequestBody AdminTaskQueryRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminQueryFacadeService.queryTasks(body));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get task detail")
    public ApiResponse<DispatchTaskDetailResponse> getTaskDetail(@PathVariable Long taskId,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(taskAdminDetailService.getEnrichedDetail(taskId));
    }

    @PostMapping("/tasks/{taskId}/auto-assign")
    @Operation(summary = "Auto-assign vehicle to task")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No vehicle available"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<DispatchTaskAssignResponse> autoAssignTask(@PathVariable Long taskId,
                                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchTaskService.autoAssignTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/manual-assign")
    @Operation(summary = "Manually assign vehicle to task")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<DispatchTaskAssignResponse> manualAssignTask(@PathVariable Long taskId,
                                                                    @Valid @RequestBody AdminTaskManualAssignRequest request,
                                                                    HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(request.getVehicleId());
        OperatorIdentity operator = resolveOperator(httpRequest);
        assignRequest.setOperatorId(operator.id());
        assignRequest.setOperatorName(operator.name());
        assignRequest.setRemark(request.getRemark());
        return ApiResponse.success(dispatchTaskService.manualAssignTask(taskId, assignRequest));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "Cancel task")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<DispatchTaskAssignResponse> cancelTask(@PathVariable Long taskId,
                                                                @RequestBody(required = false) AdminTaskCancelRequest body,
                                                                HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        OperatorIdentity operator = resolveOperator(httpRequest);
        String remark = body != null ? body.getRemark() : null;
        return ApiResponse.success(dispatchTaskService.cancelTask(taskId, operator.id(), operator.name(), remark));
    }

    @PostMapping("/tasks/{taskId}/reassign")
    @Operation(summary = "Reassign task to a different vehicle")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reassignment result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<DispatchTaskAssignResponse> reassignTask(@PathVariable Long taskId,
                                                                @Valid @RequestBody AdminTaskManualAssignRequest request,
                                                                HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(request.getVehicleId());
        OperatorIdentity operator = resolveOperator(httpRequest);
        assignRequest.setOperatorId(operator.id());
        assignRequest.setOperatorName(operator.name());
        assignRequest.setRemark(request.getRemark());
        return ApiResponse.success(dispatchTaskService.reassignTask(taskId, assignRequest));
    }

    @PostMapping("/tasks/{taskId}/bump-priority")
    @Operation(summary = "Bump task priority")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Priority bumped"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> bumpTaskPriority(@PathVariable Long taskId,
                                              HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        taskPriorityAdminService.bumpTaskPriority(taskId);
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/batch/auto-assign")
    @Operation(summary = "Batch auto-assign tasks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminBatchTaskResultResponse> batchAutoAssign(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                     HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchAutoAssign(request, operator.id(), operator.name()));
    }

    @PostMapping("/tasks/batch/cancel")
    @Operation(summary = "Batch cancel tasks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminBatchTaskResultResponse> batchCancel(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                 HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchCancel(request, operator.id(), operator.name()));
    }

    @PostMapping("/tasks/batch/reassign")
    @Operation(summary = "Batch reassign tasks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminBatchTaskResultResponse> batchReassign(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                   HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchReassign(request, operator.id(), operator.name()));
    }

    @PostMapping("/tasks/batch/unassign")
    @Operation(summary = "Batch unassign tasks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminBatchTaskResultResponse> batchUnassign(@Valid @RequestBody AdminBatchTaskRequest request,
                                                                   HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        OperatorIdentity operator = resolveOperator(httpRequest);
        return ApiResponse.success(batchTaskAdminService.batchUnassign(request, operator.id(), operator.name()));
    }

    @GetMapping("/exceptions")
    @Operation(summary = "List dispatch exceptions")
    public ApiResponse<List<DispatchExceptionListItemResponse>> listExceptions(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAdminQueryService.listExceptions());
    }

    @PostMapping("/exceptions/query")
    @Operation(summary = "Query exceptions with pagination")
    public ApiResponse<PageResponse<DispatchExceptionListItemResponse>> queryExceptions(
            @Valid @RequestBody AdminExceptionQueryRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminQueryFacadeService.queryExceptions(body));
    }

    @GetMapping("/dispatch/intervention-queue")
    @Operation(summary = "Get intervention queue", description = "Tasks and exceptions awaiting manual intervention")
    public ApiResponse<DispatchInterventionQueueResponse> getInterventionQueue(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAdminQueryService.getInterventionQueue(parkId));
    }

    @PostMapping("/dispatch/task-pool/query")
    @Operation(summary = "Query task pool with server-side pagination")
    public ApiResponse<PageResponse<DispatchTaskListItemResponse>> queryTaskPool(
            @Valid @RequestBody AdminTaskQueryRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminQueryFacadeService.queryTasks(body));
    }

    @GetMapping("/dispatch/workbench")
    @Operation(summary = "Dispatch workbench overview", description = "Real-time task pool, vehicle status, and station summary for the workbench UI")
    public ApiResponse<DispatchWorkbenchResponse> getDispatchWorkbench(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAdminQueryService.getWorkbench(parkId));
    }

    @PostMapping("/exceptions/{exceptionId}/resolve")
    @Operation(summary = "Resolve dispatch exception")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exception resolved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> resolveException(@PathVariable Long exceptionId,
                                              @Valid @RequestBody AdminDispatchExceptionResolveRequest request,
                                              HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        DispatchExceptionRecordEntity exception = dispatchExceptionService.getException(exceptionId);
        if (exception == null) {
            throw new BusinessException("DISPATCH_EXCEPTION_NOT_FOUND", "Dispatch exception not found");
        }
        if ("RESOLVED".equals(exception.getExceptionStatus())) {
            throw new BusinessException("DISPATCH_EXCEPTION_ALREADY_RESOLVED", "Dispatch exception already resolved");
        }
        if ("REASSIGN".equals(request.getAction())) {
            if (request.getVehicleId() == null) {
                throw new BusinessException("DISPATCH_EXCEPTION_VEHICLE_REQUIRED", "Reassign action requires vehicleId");
            }
            DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
            assignRequest.setVehicleId(request.getVehicleId());
            assignRequest.setOperatorId(request.getResolverId());
            assignRequest.setOperatorName(request.getResolverName());
            assignRequest.setRemark(request.getRemark());
            dispatchTaskService.reassignTask(exception.getTaskId(), assignRequest);
        }
        dispatchExceptionService.resolveException(exceptionId, request.toDispatchRequest());
        return ApiResponse.success(null);
    }

    @PostMapping("/exceptions/batch-resolve")
    @Operation(summary = "Batch resolve dispatch exceptions")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exceptions resolved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> batchResolveExceptions(@Valid @RequestBody AdminDispatchExceptionBatchResolveRequest request,
                                                    HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        dispatchExceptionService.resolveExceptions(request.getExceptionIds(), request.toDispatchRequest());
        return ApiResponse.success(null);
    }

    @GetMapping("/vehicles")
    @Operation(summary = "List all vehicles")
    public ApiResponse<List<VehicleAdminListItemResponse>> listVehicles(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(vehicleAdminQueryService.listVehicles());
    }

    @PostMapping("/vehicles/query")
    @Operation(summary = "Query vehicles with pagination and filters")
    public ApiResponse<PageResponse<VehicleAdminListItemResponse>> queryVehicles(
            @Valid @RequestBody AdminVehicleQueryRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminQueryFacadeService.queryVehicles(body));
    }

    @GetMapping("/vehicles/{vehicleId}")
    @Operation(summary = "Get vehicle detail")
    public ApiResponse<VehicleAdminDetailResponse> getVehicleDetail(@PathVariable Long vehicleId,
                                                                    HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(vehicleAdminQueryService.getVehicleDetail(vehicleId));
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Dashboard summary", description = "Aggregated counts for tasks, vehicles, orders, and exceptions")
    public ApiResponse<AdminDashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminDashboardService.getSummary(parkId));
    }

    @GetMapping("/parks")
    @Operation(summary = "List parks", description = "Admin token or X-Mobile-Api-Key for mobile order page")
    @SecurityRequirement(name = "")
    public ApiResponse<List<ParkResponse>> listParks(HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        return ApiResponse.success(parkPilotService.listParks());
    }

    @GetMapping("/park/layout")
    @Operation(summary = "Get park layout", description = "Map layout with stations, roads, and vehicle positions")
    @SecurityRequirement(name = "")
    public ApiResponse<ParkLayoutResponse> getParkLayout(@RequestParam(required = false) Long parkId,
                                                         HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        if (parkId == null) {
            return ApiResponse.success(parkPilotService.getLayout());
        }
        return ApiResponse.success(parkPilotService.getLayout(parkId));
    }

    @GetMapping("/park/geo/calibration-points")
    @Operation(summary = "List geo calibration points")
    public ApiResponse<List<GeoCalibrationPointResponse>> listGeoCalibrationPoints(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(coordinateTransformService.listCalibrationPoints(parkId));
    }

    @GetMapping("/park/geo/transform")
    @Operation(summary = "Transform geo coordinates", description = "Convert between park-local coordinates and GCJ-02")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transform result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Must provide parkX/parkY or longitude/latitude")
    })
    public ApiResponse<GeoTransformResponse> transformGeoCoordinates(
            @RequestParam(required = false) BigDecimal parkX,
            @RequestParam(required = false) BigDecimal parkY,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal latitude,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
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

    @GetMapping("/park/metadata")
    @Operation(summary = "Get park geo metadata", description = "阶段七 7.3：返回园区锚点/尺寸/场景标识等元数据，供前端替代硬编码 ZJF_PILOT_GEO")
    @SecurityRequirement(name = "")
    public ApiResponse<java.util.Map<String, Object>> getParkMetadata(@RequestParam(required = false) Long parkId,
                                                                       HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        ParkEntity park;
        if (parkId != null) {
            park = parkMapper.selectById(parkId);
        } else {
            Page<ParkEntity> parkPage = parkMapper.selectPage(new Page<>(1, 1, false),
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<ParkEntity>lambdaQuery()
                            .eq(ParkEntity::getDefaultFlag, 1)
                            .eq(ParkEntity::getDeleted, 0));
            List<ParkEntity> parkRecords = parkPage.getRecords();
            park = parkRecords.isEmpty() ? null : parkRecords.get(0);
        }
        if (park == null) {
            return ApiResponse.failure("PARK_NOT_FOUND", "Park not found");
        }
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("parkId", park.getId());
        metadata.put("parkCode", park.getParkCode());
        metadata.put("parkName", park.getParkName());
        metadata.put("scenarioCode", park.getScenarioCode());
        metadata.put("anchorLng", park.getAnchorLng());
        metadata.put("anchorLat", park.getAnchorLat());
        metadata.put("centerLng", park.getCenterLng());
        metadata.put("centerLat", park.getCenterLat());
        metadata.put("parkWidthPx", park.getMapWidth());
        metadata.put("parkHeightPx", park.getMapHeight());
        metadata.put("parkWidthMeters", park.getParkWidthMeters());
        metadata.put("parkHeightMeters", park.getParkHeightMeters());
        metadata.put("mapProvider", park.getMapProvider());
        return ApiResponse.success(metadata);
    }

    @GetMapping("/park/stations")
    @Operation(summary = "List park stations", description = "Admin token or X-Mobile-Api-Key for mobile order page")
    @SecurityRequirement(name = "")
    public ApiResponse<List<ParkStationResponse>> listParkStations(@RequestParam(required = false) Long parkId,
                                                                   HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        if (parkId == null) {
            return ApiResponse.success(parkPilotService.listStations());
        }
        return ApiResponse.success(parkPilotService.listStations(parkId));
    }

    @GetMapping("/park/vehicles")
    @Operation(summary = "List park vehicle snapshots", description = "Admin token or X-Mobile-Api-Key for order tracking")
    @SecurityRequirement(name = "")
    public ApiResponse<List<ParkVehicleSnapshotResponse>> listParkVehicles(HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        return ApiResponse.success(parkPilotService.listVehicleSnapshots());
    }

    @GetMapping("/park/geofences")
    @Operation(summary = "List park geofences", description = "Admin token or X-Mobile-Api-Key for mobile order page")
    @SecurityRequirement(name = "")
    public ApiResponse<List<ParkGeofenceResponse>> listParkGeofences(@RequestParam(required = false) Long parkId,
                                                                     HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        return ApiResponse.success(parkPilotService.listGeofences(parkId));
    }

    @GetMapping("/park/overview")
    @Operation(summary = "List park overviews", description = "Summary stats for all parks")
    public ApiResponse<List<ParkOverviewResponse>> listParkOverview(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(parkPilotService.listParkOverview());
    }

    @GetMapping("/park/orders")
    @Operation(summary = "List park order snapshots")
    @SecurityRequirement(name = "")
    public ApiResponse<List<ParkOrderSnapshotResponse>> listParkOrders(HttpServletRequest request) {
        requireAdminOrMobileOrderKey(request);
        return ApiResponse.success(parkPilotService.listOrderSnapshots());
    }

    @PostMapping("/park/orders")
    @Operation(summary = "Create park order via mobile API key", description = "Authenticated by X-Mobile-Api-Key header (SEC-06: query param no longer accepted)", security = {})
    @SecurityRequirement(name = "")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid mobile API key or validation error")
    })
    public ApiResponse<ParkOrderCreateResponse> createParkOrder(@Valid @RequestBody ParkOrderCreateRequest request,
                                                                  jakarta.servlet.http.HttpServletRequest httpRequest) {
        requireAdminOrMobileOrderKey(httpRequest);
        return ApiResponse.success(parkPilotCommandService.createParkOrder(request));
    }

    private void requireAdminOrMobileOrderKey(HttpServletRequest request) {
        bindAdminTokenIfPresent(request);
        if (AdminAuthSupport.fromRequest(request) != null) {
            AdminAuthSupport.requireAuth(request);
            return;
        }
        // SEC-06 fix: mobile API key must be supplied via the X-Mobile-Api-Key header only.
        // Query-string credentials leak into access logs and referrer headers.
        String mobileKey = request.getHeader("X-Mobile-Api-Key");
        mobileOrderAuthService.validateMobileOrderKey(mobileKey);
    }

    /** 拦截器可选鉴权路径未绑定时，从 X-Admin-Token 补绑（M8-R7 / 工作台带 token 访问 park API）。 */
    private void bindAdminTokenIfPresent(HttpServletRequest request) {
        if (AdminAuthSupport.fromRequest(request) != null) {
            return;
        }
        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.isBlank()) {
            return;
        }
        AdminAuthContext context = adminAuthService.resolveToken(token);
        if (context == null) {
            return;
        }
        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, context.getRole().name());
        request.setAttribute(AdminAuthSupport.ADMIN_USER_ID_ATTRIBUTE, context.getUserId());
        request.setAttribute(AdminAuthSupport.ADMIN_USERNAME_ATTRIBUTE, context.getUsername());
        request.setAttribute(AdminAuthSupport.ADMIN_DISPLAY_NAME_ATTRIBUTE, context.getDisplayName());
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
