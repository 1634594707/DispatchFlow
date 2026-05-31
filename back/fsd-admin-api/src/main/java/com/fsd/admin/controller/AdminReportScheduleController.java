package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminReportScheduleUpsertRequest;
import com.fsd.admin.service.ReportScheduleAdminService;
import com.fsd.admin.vo.AdminReportScheduleResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/report-schedules")
@Tag(name = "Report Schedules", description = "Scheduled analytics email delivery")
@SecurityRequirement(name = "adminToken")
public class AdminReportScheduleController {

    private final ReportScheduleAdminService reportScheduleAdminService;

    public AdminReportScheduleController(ReportScheduleAdminService reportScheduleAdminService) {
        this.reportScheduleAdminService = reportScheduleAdminService;
    }

    @GetMapping
    @Operation(summary = "List report schedules")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedule list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminReportScheduleResponse>> list(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(reportScheduleAdminService.list());
    }

    @PostMapping
    @Operation(summary = "Create or update report schedule")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedule saved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminReportScheduleResponse> upsert(@Valid @RequestBody AdminReportScheduleUpsertRequest body,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(reportScheduleAdminService.upsert(body));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete report schedule")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedule deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        reportScheduleAdminService.delete(id);
        return ApiResponse.success(null);
    }
}
