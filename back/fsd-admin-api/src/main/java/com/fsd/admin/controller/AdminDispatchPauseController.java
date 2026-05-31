package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminDispatchPauseRequest;
import com.fsd.admin.vo.AdminDispatchPauseStatusResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fsd.dispatch.service.DispatchPauseControlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dispatch/pause")
@Tag(name = "Dispatch Pause", description = "Global or per-park dispatch pause control")
@SecurityRequirement(name = "adminToken")
public class AdminDispatchPauseController {

    private final DispatchPauseControlService dispatchPauseControlService;

    public AdminDispatchPauseController(DispatchPauseControlService dispatchPauseControlService) {
        this.dispatchPauseControlService = dispatchPauseControlService;
    }

    @GetMapping
    @Operation(summary = "Get dispatch pause status", description = "Returns whether dispatch is paused globally or for a specific park")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pause status returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminDispatchPauseStatusResponse> status(@RequestParam(required = false) Long parkId,
                                                                HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(AdminDispatchPauseStatusResponse.builder()
                .parkId(parkId)
                .globalPaused(dispatchPauseControlService.isGlobalDispatchPaused())
                .parkPaused(parkId != null && dispatchPauseControlService.isDispatchPaused(parkId))
                .build());
    }

    @PostMapping
    @Operation(summary = "Set dispatch pause state", description = "Pause or resume dispatch globally or for a specific park")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pause state updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminDispatchPauseStatusResponse> setPaused(@Valid @RequestBody AdminDispatchPauseRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        dispatchPauseControlService.setDispatchPaused(body.getParkId(), body.getPaused());
        return ApiResponse.success(AdminDispatchPauseStatusResponse.builder()
                .parkId(body.getParkId())
                .globalPaused(dispatchPauseControlService.isGlobalDispatchPaused())
                .parkPaused(body.getParkId() != null && dispatchPauseControlService.isDispatchPaused(body.getParkId()))
                .build());
    }
}
