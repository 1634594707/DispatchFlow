package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.ReportScheduleAdminService;
import com.fsd.admin.vo.AdminReportScheduleResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
public class AdminReportScheduleController {

    private final ReportScheduleAdminService reportScheduleAdminService;

    public AdminReportScheduleController(ReportScheduleAdminService reportScheduleAdminService) {
        this.reportScheduleAdminService = reportScheduleAdminService;
    }

    @GetMapping
    public ApiResponse<List<AdminReportScheduleResponse>> list(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(reportScheduleAdminService.list());
    }

    @PostMapping
    public ApiResponse<AdminReportScheduleResponse> upsert(@RequestBody AdminReportScheduleResponse body,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(reportScheduleAdminService.upsert(body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        reportScheduleAdminService.delete(id);
        return ApiResponse.success(null);
    }
}
