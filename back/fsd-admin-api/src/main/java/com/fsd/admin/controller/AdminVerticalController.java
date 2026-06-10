package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminAutomationRuleUpsertRequest;
import com.fsd.admin.dto.AdminDispatchRouteUpsertRequest;
import com.fsd.admin.dto.AdminPeakModeUpsertRequest;
import com.fsd.admin.service.OpsSnapshotAdminService;
import com.fsd.admin.service.VerticalAdminService;
import com.fsd.admin.vo.AdminAutomationRuleAuditResponse;
import com.fsd.admin.vo.AdminAutomationRuleResponse;
import com.fsd.admin.vo.AdminDispatchRouteResponse;
import com.fsd.admin.vo.AdminHubOverviewResponse;
import com.fsd.admin.vo.AdminOpsSnapshotResponse;
import com.fsd.admin.vo.AdminPeakModeResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/vertical")
@Tag(name = "Vertical", description = "Home-textile routes, peak mode, hub, automation rules, and ops snapshot")
@SecurityRequirement(name = "adminToken")
public class AdminVerticalController {

    private final VerticalAdminService verticalAdminService;
    private final OpsSnapshotAdminService opsSnapshotAdminService;

    public AdminVerticalController(VerticalAdminService verticalAdminService,
                                   OpsSnapshotAdminService opsSnapshotAdminService) {
        this.verticalAdminService = verticalAdminService;
        this.opsSnapshotAdminService = opsSnapshotAdminService;
    }

    @GetMapping("/routes")
    @Operation(summary = "List dispatch routes")
    public ApiResponse<List<AdminDispatchRouteResponse>> listRoutes(@RequestParam(required = false) Long parkId,
                                                                    HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(verticalAdminService.listRoutes(parkId));
    }

    @PostMapping("/routes")
    @Operation(summary = "Create dispatch route")
    public ApiResponse<AdminDispatchRouteResponse> createRoute(@Valid @RequestBody AdminDispatchRouteUpsertRequest body,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(verticalAdminService.createRoute(body));
    }

    @PutMapping("/routes/{routeId}")
    @Operation(summary = "Update dispatch route")
    public ApiResponse<AdminDispatchRouteResponse> updateRoute(@PathVariable Long routeId,
                                                               @Valid @RequestBody AdminDispatchRouteUpsertRequest body,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(verticalAdminService.updateRoute(routeId, body));
    }

    @PostMapping("/routes/{routeId}/toggle-status")
    @Operation(summary = "Toggle route active status")
    public ApiResponse<AdminDispatchRouteResponse> toggleRoute(@PathVariable Long routeId,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(verticalAdminService.toggleRouteStatus(routeId));
    }

    @GetMapping("/peak-mode")
    @Operation(summary = "Get peak mode config")
    public ApiResponse<AdminPeakModeResponse> getPeakMode(@RequestParam Long parkId,
                                                          HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(verticalAdminService.getPeakMode(parkId));
    }

    @PutMapping("/peak-mode")
    @Operation(summary = "Update peak mode config")
    public ApiResponse<AdminPeakModeResponse> updatePeakMode(@Valid @RequestBody AdminPeakModeUpsertRequest body,
                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(verticalAdminService.updatePeakMode(body));
    }

    @GetMapping("/hub-overview")
    @Operation(summary = "Get hub overview")
    public ApiResponse<AdminHubOverviewResponse> getHubOverview(@RequestParam(required = false) Long parkId,
                                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(verticalAdminService.getHubOverview(parkId));
    }

    @GetMapping("/ops-snapshot")
    @Operation(summary = "Get ops snapshot")
    public ApiResponse<AdminOpsSnapshotResponse> getOpsSnapshot(@RequestParam(required = false) Long parkId,
                                                                HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(opsSnapshotAdminService.getSnapshot(parkId));
    }

    @GetMapping("/automation-rules")
    @Operation(summary = "List automation rules")
    public ApiResponse<List<AdminAutomationRuleResponse>> listRules(@RequestParam(required = false) Long parkId,
                                                                    HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(verticalAdminService.listAutomationRules(parkId));
    }

    @PostMapping("/automation-rules")
    @Operation(summary = "Create automation rule")
    public ApiResponse<AdminAutomationRuleResponse> createRule(@Valid @RequestBody AdminAutomationRuleUpsertRequest body,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        AdminAuthContext ctx = AdminAuthSupport.fromRequest(request);
        return ApiResponse.success(verticalAdminService.createAutomationRule(body, ctx.getUsername()));
    }

    @PutMapping("/automation-rules/{ruleId}")
    @Operation(summary = "Update automation rule")
    public ApiResponse<AdminAutomationRuleResponse> updateRule(@PathVariable Long ruleId,
                                                               @Valid @RequestBody AdminAutomationRuleUpsertRequest body,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        AdminAuthContext ctx = AdminAuthSupport.fromRequest(request);
        return ApiResponse.success(verticalAdminService.updateAutomationRule(ruleId, body, ctx.getUsername()));
    }

    @DeleteMapping("/automation-rules/{ruleId}")
    @Operation(summary = "Delete automation rule")
    public ApiResponse<Void> deleteRule(@PathVariable Long ruleId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        AdminAuthContext ctx = AdminAuthSupport.fromRequest(request);
        verticalAdminService.deleteAutomationRule(ruleId, ctx.getUsername());
        return ApiResponse.success(null);
    }

    @PostMapping("/automation-rules/{ruleId}/toggle")
    @Operation(summary = "Toggle automation rule enabled/disabled")
    public ApiResponse<AdminAutomationRuleResponse> toggleRule(@PathVariable Long ruleId,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        AdminAuthContext ctx = AdminAuthSupport.fromRequest(request);
        return ApiResponse.success(verticalAdminService.toggleAutomationRule(ruleId, ctx.getUsername()));
    }

    @GetMapping("/automation-rules/{ruleId}/audit")
    @Operation(summary = "List automation rule audit log")
    public ApiResponse<List<AdminAutomationRuleAuditResponse>> listRuleAudit(@PathVariable Long ruleId,
                                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(verticalAdminService.listAutomationRuleAudit(ruleId));
    }
}
