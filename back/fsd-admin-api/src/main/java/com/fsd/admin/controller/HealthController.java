package com.fsd.admin.controller;

import com.fsd.admin.service.SystemHealthAdminService;
import com.fsd.admin.vo.AdminSystemHealthResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Public liveness probe")
public class HealthController {

    private final SystemHealthAdminService systemHealthAdminService;

    public HealthController(SystemHealthAdminService systemHealthAdminService) {
        this.systemHealthAdminService = systemHealthAdminService;
    }

    @GetMapping({"", "/live"})
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "fsd-core-server",
                "status", "UP",
                "probe", "liveness",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        AdminSystemHealthResponse health = systemHealthAdminService.getHealth();
        Map<String, Object> payload = Map.of(
                "service", "fsd-core-server",
                "status", health.getOverallStatus(),
                "probe", "readiness",
                "checkedAt", health.getCheckedAt().toString(),
                "components", health.getComponents()
        );
        HttpStatus httpStatus = "DOWN".equals(health.getOverallStatus()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(payload));
    }
}
