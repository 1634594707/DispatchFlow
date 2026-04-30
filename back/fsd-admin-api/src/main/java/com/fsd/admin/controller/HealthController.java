package com.fsd.admin.controller;

import com.fsd.common.model.ApiResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "fsd-core-server",
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
