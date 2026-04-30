package com.fsd.dispatch.controller;

import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchTaskService dispatchTaskService;
    private final DispatchExceptionService dispatchExceptionService;

    public DispatchController(DispatchTaskService dispatchTaskService,
                              DispatchExceptionService dispatchExceptionService) {
        this.dispatchTaskService = dispatchTaskService;
        this.dispatchExceptionService = dispatchExceptionService;
    }

    @PostMapping("/tasks")
    public ApiResponse<DispatchTaskCreateResponse> createTask(@Valid @RequestBody DispatchTaskCreateRequest request) {
        return ApiResponse.success(dispatchTaskService.createTask(request));
    }

    @PostMapping("/tasks/{taskId}/auto-assign")
    public ApiResponse<DispatchTaskAssignResponse> autoAssign(@PathVariable Long taskId) {
        return ApiResponse.success(dispatchTaskService.autoAssignTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/manual-assign")
    public ApiResponse<DispatchTaskAssignResponse> manualAssign(@PathVariable Long taskId,
                                                                @Valid @RequestBody DispatchTaskManualAssignRequest request) {
        return ApiResponse.success(dispatchTaskService.manualAssignTask(taskId, request));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DispatchTaskDetailResponse> getTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.success(dispatchTaskService.getTaskDetail(taskId));
    }

    @GetMapping("/tasks/manual-pending")
    public ApiResponse<List<DispatchTaskListItemResponse>> listManualPendingTasks() {
        return ApiResponse.success(dispatchTaskService.listManualPendingTasks());
    }

    @GetMapping("/summary")
    public ApiResponse<DispatchSummaryResponse> summary() {
        return ApiResponse.success(dispatchTaskService.getSummary());
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<DispatchExceptionRecordEntity>> listOpenExceptions() {
        return ApiResponse.success(dispatchExceptionService.listOpenExceptions());
    }

    @PostMapping("/exceptions/{exceptionId}/resolve")
    public ApiResponse<Void> resolveException(@PathVariable Long exceptionId,
                                              @Valid @RequestBody DispatchExceptionResolveRequest request) {
        dispatchExceptionService.resolveException(exceptionId, request);
        return ApiResponse.success(null);
    }
}
