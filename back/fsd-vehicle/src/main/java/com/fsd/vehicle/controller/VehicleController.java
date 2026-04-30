package com.fsd.vehicle.controller;

import com.fsd.common.model.ApiResponse;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleReportResponse;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleReportService vehicleReportService;

    public VehicleController(VehicleService vehicleService, VehicleReportService vehicleReportService) {
        this.vehicleService = vehicleService;
        this.vehicleReportService = vehicleReportService;
    }

    @GetMapping("/summary")
    public ApiResponse<VehicleSummaryResponse> summary() {
        return ApiResponse.success(vehicleService.getSummary());
    }

    @PostMapping("/reports")
    public ApiResponse<VehicleReportResponse> report(@Valid @RequestBody VehicleReportRequest request) {
        return ApiResponse.success(vehicleReportService.handleReport(request));
    }
}
