package com.fsd.dispatch.controller;

import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.dto.VehicleCommandFailRequest;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.service.VehicleCommandService;
import com.fsd.dispatch.service.VehicleGatewayService;
import com.fsd.dispatch.vo.VehicleCommandResponse;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.vo.VehicleReportResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicle-gateway")
public class VehicleGatewayController {

    private final VehicleGatewayService vehicleGatewayService;
    private final VehicleCommandService vehicleCommandService;

    public VehicleGatewayController(VehicleGatewayService vehicleGatewayService,
                                    VehicleCommandService vehicleCommandService) {
        this.vehicleGatewayService = vehicleGatewayService;
        this.vehicleCommandService = vehicleCommandService;
    }

    @PostMapping("/telemetry")
    public ApiResponse<Void> telemetry(@Valid @RequestBody VehicleTelemetryRequest request) {
        vehicleGatewayService.ingestTelemetry(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/reports")
    public ApiResponse<VehicleReportResponse> report(@Valid @RequestBody VehicleReportRequest request) {
        return ApiResponse.success(vehicleGatewayService.handleReport(request));
    }

    @GetMapping("/commands/next")
    public ApiResponse<VehicleCommandResponse> pollNextCommand() {
        return ApiResponse.success(vehicleCommandService.pollNextCommand(requireVehicleCode()));
    }

    @PostMapping("/commands/{commandId}/ack")
    public ApiResponse<Void> acknowledge(@PathVariable Long commandId) {
        vehicleCommandService.acknowledgeCommand(requireVehicleCode(), commandId);
        return ApiResponse.success(null);
    }

    @PostMapping("/commands/{commandId}/fail")
    public ApiResponse<Void> fail(@PathVariable Long commandId, @Valid @RequestBody VehicleCommandFailRequest request) {
        vehicleCommandService.failCommand(requireVehicleCode(), commandId, request.getReason());
        return ApiResponse.success(null);
    }

    private String requireVehicleCode() {
        String vehicleCode = VehicleGatewayAuthContext.getVehicleCode();
        if (vehicleCode == null || vehicleCode.isBlank()) {
            throw new com.fsd.common.exception.BusinessException("VEHICLE_AUTH_REQUIRED", "Vehicle authentication required");
        }
        return vehicleCode;
    }
}
