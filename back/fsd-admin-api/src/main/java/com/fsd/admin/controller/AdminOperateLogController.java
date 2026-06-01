package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminOperateLogQueryRequest;
import com.fsd.admin.service.OperateLogAdminService;
import com.fsd.admin.vo.AdminOperateLogResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.common.model.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operate-logs")
@Tag(name = "Operate Logs", description = "Audit trail query and export")
@SecurityRequirement(name = "adminToken")
public class AdminOperateLogController {

    private final OperateLogAdminService operateLogAdminService;

    public AdminOperateLogController(OperateLogAdminService operateLogAdminService) {
        this.operateLogAdminService = operateLogAdminService;
    }

    @PostMapping("/query")
    @Operation(summary = "Query operation logs with pagination")
    public ApiResponse<PageResponse<AdminOperateLogResponse>> queryLogs(
            @RequestBody AdminOperateLogQueryRequest request,
            HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAdmin(httpRequest);
        return ApiResponse.success(operateLogAdminService.queryLogs(request));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "List operation logs by task")
    public ApiResponse<List<AdminOperateLogResponse>> listByTask(@PathVariable Long taskId,
                                                                 HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAdmin(httpRequest);
        return ApiResponse.success(operateLogAdminService.listByTaskId(taskId));
    }

    @GetMapping("/vehicles/{vehicleId}")
    @Operation(summary = "List operation logs by vehicle")
    public ApiResponse<List<AdminOperateLogResponse>> listByVehicle(@PathVariable Long vehicleId,
                                                                    HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAdmin(httpRequest);
        return ApiResponse.success(operateLogAdminService.listByVehicleId(vehicleId));
    }

    @PostMapping("/export")
    @Operation(summary = "Export operation logs as CSV")
    public ApiResponse<String> exportCsv(@RequestBody AdminOperateLogQueryRequest request,
                                         HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAdmin(httpRequest);
        return ApiResponse.success(operateLogAdminService.exportCsv(request));
    }
}
