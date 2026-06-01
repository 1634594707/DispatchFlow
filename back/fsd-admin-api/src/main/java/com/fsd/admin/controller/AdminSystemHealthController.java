package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.SystemHealthAdminService;
import com.fsd.admin.vo.AdminSystemHealthResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@Tag(name = "System Health", description = "Dependency and subsystem health snapshot")
@SecurityRequirement(name = "adminToken")
public class AdminSystemHealthController {

    private final SystemHealthAdminService systemHealthAdminService;

    public AdminSystemHealthController(SystemHealthAdminService systemHealthAdminService) {
        this.systemHealthAdminService = systemHealthAdminService;
    }

    @GetMapping("/health")
    @Operation(summary = "System health check", description = "Returns health status of MySQL, Redis, RabbitMQ, and other dependencies")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Health status returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminSystemHealthResponse> health(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(systemHealthAdminService.getHealth());
    }
}
