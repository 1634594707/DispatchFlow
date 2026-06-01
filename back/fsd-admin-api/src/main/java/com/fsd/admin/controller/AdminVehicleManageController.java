package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminVehicleMaintenanceUpsertRequest;
import com.fsd.admin.dto.AdminVehicleUpsertRequest;
import com.fsd.admin.service.VehicleAdminManageService;
import com.fsd.admin.service.VehicleHealthAdminService;
import com.fsd.admin.service.VehicleTrajectoryAdminService;
import com.fsd.admin.vo.AdminTrajectoryDwellResponse;
import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import com.fsd.admin.vo.AdminVehicleHealthResponse;
import com.fsd.admin.vo.AdminVehicleCredentialResponse;
import com.fsd.admin.vo.AdminVehicleMaintenanceResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/vehicles/manage")
@Tag(name = "Vehicle Management", description = "Vehicle CRUD, credentials, maintenance, health, and trajectory")
@SecurityRequirement(name = "adminToken")
public class AdminVehicleManageController {

    private final VehicleAdminManageService vehicleAdminManageService;
    private final VehicleHealthAdminService vehicleHealthAdminService;
    private final VehicleTrajectoryAdminService vehicleTrajectoryAdminService;

    public AdminVehicleManageController(VehicleAdminManageService vehicleAdminManageService,
                                        VehicleHealthAdminService vehicleHealthAdminService,
                                        VehicleTrajectoryAdminService vehicleTrajectoryAdminService) {
        this.vehicleAdminManageService = vehicleAdminManageService;
        this.vehicleHealthAdminService = vehicleHealthAdminService;
        this.vehicleTrajectoryAdminService = vehicleTrajectoryAdminService;
    }

    @PostMapping
    @Operation(summary = "Create vehicle")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicle created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<VehicleAdminDetailResponse> createVehicle(@Valid @RequestBody AdminVehicleUpsertRequest body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleAdminManageService.createVehicle(body));
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Update vehicle")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<VehicleAdminDetailResponse> updateVehicle(@PathVariable Long vehicleId,
                                                                 @Valid @RequestBody AdminVehicleUpsertRequest body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleAdminManageService.updateVehicle(vehicleId, body));
    }

    @PostMapping("/{vehicleId}/disable")
    @Operation(summary = "Disable vehicle")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicle disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> disableVehicle(@PathVariable Long vehicleId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        vehicleAdminManageService.disableVehicle(vehicleId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{vehicleId}/credentials")
    @Operation(summary = "List vehicle credentials")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credential list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminVehicleCredentialResponse>> listCredentials(@PathVariable Long vehicleId,
                                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleAdminManageService.listCredentials(vehicleId));
    }

    @PostMapping("/{vehicleId}/credentials")
    @Operation(summary = "Create vehicle credential")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credential created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminVehicleCredentialResponse> createCredential(@PathVariable Long vehicleId,
                                                                          HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleAdminManageService.createCredential(vehicleId));
    }

    @PostMapping("/credentials/{credentialId}/disable")
    @Operation(summary = "Disable vehicle credential")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credential disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> disableCredential(@PathVariable Long credentialId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        vehicleAdminManageService.disableCredential(credentialId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{vehicleId}/maintenance")
    @Operation(summary = "List vehicle maintenance records")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Maintenance records returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminVehicleMaintenanceResponse>> listMaintenance(@PathVariable Long vehicleId,
                                                                              HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleAdminManageService.listMaintenanceRecords(vehicleId));
    }

    @PostMapping("/maintenance")
    @Operation(summary = "Create maintenance record")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Record created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminVehicleMaintenanceResponse> createMaintenance(
            @Valid @RequestBody AdminVehicleMaintenanceUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        String operatorName = context.getDisplayName() != null ? context.getDisplayName() : context.getUsername();
        return ApiResponse.success(vehicleAdminManageService.createMaintenanceRecord(body, operatorName));
    }

    @GetMapping("/{vehicleId}/health")
    @Operation(summary = "Vehicle health status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Health status returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminVehicleHealthResponse> health(@PathVariable Long vehicleId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(vehicleHealthAdminService.getHealth(vehicleId));
    }

    @GetMapping("/{vehicleId}/trajectory")
    @Operation(summary = "Vehicle trajectory", description = "Historical position trace with optional time range and source filter")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trajectory data returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<List<AdminTrajectoryPointResponse>> trajectory(@PathVariable Long vehicleId,
                                                                      @RequestParam(required = false) String source,
                                                                      @RequestParam(required = false)
                                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                      LocalDateTime from,
                                                                      @RequestParam(required = false)
                                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                      LocalDateTime to,
                                                                      HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(vehicleTrajectoryAdminService.getTrajectory(vehicleId, from, to, source));
    }

    @GetMapping("/{vehicleId}/trajectory/dwell")
    @Operation(summary = "Vehicle dwell points", description = "Locations where the vehicle paused for extended periods")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dwell points returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<List<AdminTrajectoryDwellResponse>> dwellPoints(@PathVariable Long vehicleId,
                                                                       @RequestParam(required = false)
                                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                       LocalDateTime from,
                                                                       @RequestParam(required = false)
                                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                       LocalDateTime to,
                                                                       HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(vehicleTrajectoryAdminService.getDwellPoints(vehicleId, from, to));
    }
}
