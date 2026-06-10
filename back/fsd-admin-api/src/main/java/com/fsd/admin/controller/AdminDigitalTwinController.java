package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminDigitalTwinSimulateRequest;
import com.fsd.admin.service.DigitalTwinAdminService;
import com.fsd.admin.vo.AdminDigitalTwinSimulateResponse;
import com.fsd.admin.vo.AdminDigitalTwinSnapshotResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/digital-twin")
@Tag(name = "Digital Twin", description = "Park snapshot and what-if simulation")
@SecurityRequirement(name = "adminToken")
public class AdminDigitalTwinController {

    private final DigitalTwinAdminService digitalTwinAdminService;

    public AdminDigitalTwinController(DigitalTwinAdminService digitalTwinAdminService) {
        this.digitalTwinAdminService = digitalTwinAdminService;
    }

    @GetMapping("/snapshot")
    @Operation(summary = "Get park twin snapshot", description = "Current estimated state of all vehicles, stations, and tasks in the park")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Snapshot returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminDigitalTwinSnapshotResponse> snapshot(@RequestParam(required = false) Long parkId,
                                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(digitalTwinAdminService.getSnapshot(parkId));
    }

    @PostMapping("/simulate")
    @Operation(summary = "Run what-if simulation", description = "Evaluate a scenario change against the current park state")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Simulation result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid simulation parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminDigitalTwinSimulateResponse> simulate(@Valid @RequestBody AdminDigitalTwinSimulateRequest request,
                                                                  HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        return ApiResponse.success(digitalTwinAdminService.simulate(request));
    }
}
