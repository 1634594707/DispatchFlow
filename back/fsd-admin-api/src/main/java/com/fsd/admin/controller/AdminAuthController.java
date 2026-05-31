package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminChangePasswordRequest;
import com.fsd.admin.dto.AdminLoginRequest;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.vo.AdminLoginResponse;
import com.fsd.admin.vo.AdminUserResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin Auth", description = "Login, logout, password, and TOTP 2FA")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        adminAuthService.logout(token);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<AdminUserResponse> me(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminAuthService.getCurrentUser(context.getUserId()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(httpRequest);
        adminAuthService.changePassword(context.getUserId(), request);
        return ApiResponse.success(null);
    }

    @PostMapping("/totp/enroll")
    public ApiResponse<java.util.Map<String, String>> enrollTotp(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminAuthService.enrollTotp(context.getUserId()));
    }

    @PostMapping("/totp/enable")
    public ApiResponse<Void> enableTotp(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        adminAuthService.enableTotp(context.getUserId(), body.get("code"));
        return ApiResponse.success(null);
    }

    @PostMapping("/totp/disable")
    public ApiResponse<Void> disableTotp(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        adminAuthService.disableTotp(context.getUserId(), body.get("code"));
        return ApiResponse.success(null);
    }
}
