package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminChangePasswordRequest;
import com.fsd.admin.dto.AdminLoginRequest;
import com.fsd.admin.dto.AdminTotpCodeRequest;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.vo.AdminLoginResponse;
import com.fsd.admin.vo.AdminUserResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @Operation(summary = "Admin login", description = "Returns session token in `X-Admin-Token` response header and body")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid credentials or missing TOTP code")
    })
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful")
    })
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        adminAuthService.logout(token);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    @Operation(summary = "Current admin user profile")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminUserResponse> me(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminAuthService.getCurrentUser(context.getUserId()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change current user password")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid old password or weak new password"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(httpRequest);
        adminAuthService.changePassword(context.getUserId(), request);
        return ApiResponse.success(null);
    }

    @PostMapping("/totp/enroll")
    @Operation(summary = "Enroll TOTP 2FA", description = "Returns a shared secret and provisioning URI for the authenticator app")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TOTP enrollment data returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<java.util.Map<String, String>> enrollTotp(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(adminAuthService.enrollTotp(context.getUserId()));
    }

    @PostMapping("/totp/enable")
    @Operation(summary = "Enable TOTP 2FA", description = "Verify and activate TOTP with a valid code from the authenticator app")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TOTP enabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid TOTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> enableTotp(@Valid @RequestBody AdminTotpCodeRequest body, HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        adminAuthService.enableTotp(context.getUserId(), body.getCode());
        return ApiResponse.success(null);
    }

    @PostMapping("/totp/disable")
    @Operation(summary = "Disable TOTP 2FA", description = "Provide a valid TOTP code to disable two-factor authentication")
    @SecurityRequirement(name = "adminToken")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TOTP disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid TOTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<Void> disableTotp(@Valid @RequestBody AdminTotpCodeRequest body, HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        adminAuthService.disableTotp(context.getUserId(), body.getCode());
        return ApiResponse.success(null);
    }
}
