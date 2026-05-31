package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminAlertSettingsUpsertRequest;
import com.fsd.admin.service.AlertSettingsAdminService;
import com.fsd.admin.vo.AdminAlertSettingsResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/alert-settings")
@Tag(name = "Alert Settings", description = "Browser notification and exception alert preferences")
@SecurityRequirement(name = "adminToken")
public class AdminAlertSettingsController {

    private final AlertSettingsAdminService alertSettingsAdminService;

    public AdminAlertSettingsController(AlertSettingsAdminService alertSettingsAdminService) {
        this.alertSettingsAdminService = alertSettingsAdminService;
    }

    @GetMapping
    @Operation(summary = "Get alert settings for current user")
    public ApiResponse<AdminAlertSettingsResponse> get(HttpServletRequest request) {
        AdminAuthContext ctx = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(alertSettingsAdminService.getSettings(ctx.getUserId()));
    }

    @PutMapping
    @Operation(summary = "Save alert settings for current user")
    public ApiResponse<AdminAlertSettingsResponse> save(@Valid @RequestBody AdminAlertSettingsUpsertRequest body,
                                                        HttpServletRequest request) {
        AdminAuthContext ctx = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(alertSettingsAdminService.saveSettings(ctx.getUserId(), body));
    }
}
