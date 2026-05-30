package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.TrafficAdminService;
import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/traffic")
public class AdminTrafficController {

    private final TrafficAdminService trafficAdminService;

    public AdminTrafficController(TrafficAdminService trafficAdminService) {
        this.trafficAdminService = trafficAdminService;
    }

    @GetMapping("/overview")
    public ApiResponse<List<AdminTrafficSegmentResponse>> overview(@RequestParam(required = false) Long parkId,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.getTrafficOverview(parkId));
    }

    @PostMapping("/refresh-congestion")
    public ApiResponse<Void> refresh(@RequestParam(required = false) Long parkId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        trafficAdminService.refreshCongestion(parkId);
        return ApiResponse.success(null);
    }
}
