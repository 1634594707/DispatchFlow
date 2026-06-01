package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminUserCreateRequest;
import com.fsd.admin.dto.AdminUserUpdateRequest;
import com.fsd.admin.service.AdminUserService;
import com.fsd.admin.vo.AdminUserResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "User Management", description = "Admin users and RBAC roles")
@SecurityRequirement(name = "adminToken")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List admin users")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminUserResponse>> listUsers(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(adminUserService.listUsers());
    }

    @PostMapping
    @Operation(summary = "Create admin user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or username taken"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminUserResponse> createUser(@Valid @RequestBody AdminUserCreateRequest body,
                                                     HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(adminUserService.createUser(body));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update admin user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable Long userId,
                                                       @Valid @RequestBody AdminUserUpdateRequest body,
                                                       HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(adminUserService.updateUser(userId, body));
    }

    @PostMapping("/{userId}/disable")
    @Operation(summary = "Disable admin user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> disableUser(@PathVariable Long userId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        adminUserService.disableUser(userId);
        return ApiResponse.success(null);
    }
}
