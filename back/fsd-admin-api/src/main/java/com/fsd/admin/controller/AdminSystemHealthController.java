package com.fsd.admin.controller;

import com.fsd.admin.service.SystemHealthAdminService;
import com.fsd.admin.vo.AdminSystemHealthResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@Tag(name = "System Health", description = "Dependency and subsystem health snapshot")
public class AdminSystemHealthController {

    private final SystemHealthAdminService systemHealthAdminService;

    public AdminSystemHealthController(SystemHealthAdminService systemHealthAdminService) {
        this.systemHealthAdminService = systemHealthAdminService;
    }

    @GetMapping("/health")
    public ApiResponse<AdminSystemHealthResponse> health() {
        return ApiResponse.success(systemHealthAdminService.getHealth());
    }
}
