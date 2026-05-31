package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminAlertSettingsUpsertRequest;
import com.fsd.admin.service.AlertSettingsAdminService;
import com.fsd.admin.vo.AdminAlertSettingsResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/alert-settings")
public class AdminAlertSettingsController {

    private final AlertSettingsAdminService alertSettingsAdminService;

    public AdminAlertSettingsController(AlertSettingsAdminService alertSettingsAdminService) {
        this.alertSettingsAdminService = alertSettingsAdminService;
    }

    @GetMapping
    public ApiResponse<AdminAlertSettingsResponse> get(HttpServletRequest request) {
        AdminAuthContext ctx = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(alertSettingsAdminService.getSettings(ctx.getUserId()));
    }

    @PutMapping
    public ApiResponse<AdminAlertSettingsResponse> save(@Valid @RequestBody AdminAlertSettingsUpsertRequest body,
                                                        HttpServletRequest request) {
        AdminAuthContext ctx = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(alertSettingsAdminService.saveSettings(ctx.getUserId(), body));
    }
}
