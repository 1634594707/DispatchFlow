package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.vo.AdminDispatchPauseStatusResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fsd.dispatch.service.DispatchPauseControlService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dispatch/pause")
@Tag(name = "Dispatch Pause", description = "Global or per-park dispatch pause control")
public class AdminDispatchPauseController {

    private final DispatchPauseControlService dispatchPauseControlService;

    public AdminDispatchPauseController(DispatchPauseControlService dispatchPauseControlService) {
        this.dispatchPauseControlService = dispatchPauseControlService;
    }

    @GetMapping
    public ApiResponse<AdminDispatchPauseStatusResponse> status(@RequestParam(required = false) Long parkId) {
        return ApiResponse.success(AdminDispatchPauseStatusResponse.builder()
                .parkId(parkId)
                .globalPaused(dispatchPauseControlService.isGlobalDispatchPaused())
                .parkPaused(parkId != null && dispatchPauseControlService.isDispatchPaused(parkId))
                .build());
    }

    @PostMapping
    public ApiResponse<AdminDispatchPauseStatusResponse> setPaused(@RequestBody Map<String, Object> body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long parkId = body.get("parkId") == null ? null : Long.parseLong(body.get("parkId").toString());
        boolean paused = Boolean.TRUE.equals(body.get("paused"));
        dispatchPauseControlService.setDispatchPaused(parkId, paused);
        return ApiResponse.success(AdminDispatchPauseStatusResponse.builder()
                .parkId(parkId)
                .globalPaused(dispatchPauseControlService.isGlobalDispatchPaused())
                .parkPaused(parkId != null && dispatchPauseControlService.isDispatchPaused(parkId))
                .build());
    }
}
