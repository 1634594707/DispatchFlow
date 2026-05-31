package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminTrafficPauseZoneRequest;
import com.fsd.admin.service.TrafficAdminService;
import com.fsd.admin.vo.AdminTrafficPauseZoneResponse;
import com.fsd.admin.vo.AdminTrafficSegmentImpactResponse;
import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import com.fsd.admin.vo.AdminTrafficSummaryResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/summary")
    public ApiResponse<AdminTrafficSummaryResponse> summary(@RequestParam(required = false) Long parkId) {
        return ApiResponse.success(trafficAdminService.getSummary(parkId));
    }

    @PostMapping("/refresh-congestion")
    public ApiResponse<Void> refresh(@RequestParam(required = false) Long parkId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        trafficAdminService.refreshCongestion(parkId);
        return ApiResponse.success(null);
    }

    @GetMapping("/segments/{segmentId}/impact")
    public ApiResponse<AdminTrafficSegmentImpactResponse> segmentImpact(@PathVariable Long segmentId,
                                                                      HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.getSegmentImpact(segmentId));
    }

    @PostMapping("/segments/{segmentId}/disable")
    public ApiResponse<AdminTrafficSegmentResponse> disableSegment(@PathVariable Long segmentId,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.disableSegment(segmentId));
    }

    @PostMapping("/segments/{segmentId}/downgrade-congestion")
    public ApiResponse<AdminTrafficSegmentResponse> downgradeCongestion(@PathVariable Long segmentId,
                                                                          HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.downgradeCongestion(segmentId));
    }

    @GetMapping("/pause-zones")
    public ApiResponse<List<AdminTrafficPauseZoneResponse>> listPauseZones(@RequestParam(required = false) Long parkId,
                                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.listPauseZones(parkId));
    }

    @PostMapping("/pause-zones")
    public ApiResponse<AdminTrafficPauseZoneResponse> addPauseZone(@Valid @RequestBody AdminTrafficPauseZoneRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(trafficAdminService.addPauseZone(body));
    }

    @DeleteMapping("/pause-zones")
    public ApiResponse<Void> clearPauseZones(@RequestParam(required = false) Long parkId,
                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        trafficAdminService.clearPauseZones(parkId);
        return ApiResponse.success(null);
    }
}
