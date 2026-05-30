package com.fsd.admin.controller;

import com.fsd.admin.dto.AdminDigitalTwinSimulateRequest;
import com.fsd.admin.service.DigitalTwinAdminService;
import com.fsd.admin.vo.AdminDigitalTwinSimulateResponse;
import com.fsd.admin.vo.AdminDigitalTwinSnapshotResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/digital-twin")
public class AdminDigitalTwinController {

    private final DigitalTwinAdminService digitalTwinAdminService;

    public AdminDigitalTwinController(DigitalTwinAdminService digitalTwinAdminService) {
        this.digitalTwinAdminService = digitalTwinAdminService;
    }

    @GetMapping("/snapshot")
    public ApiResponse<AdminDigitalTwinSnapshotResponse> snapshot(@RequestParam(required = false) Long parkId) {
        return ApiResponse.success(digitalTwinAdminService.getSnapshot(parkId));
    }

    @PostMapping("/simulate")
    public ApiResponse<AdminDigitalTwinSimulateResponse> simulate(@Valid @RequestBody AdminDigitalTwinSimulateRequest request) {
        return ApiResponse.success(digitalTwinAdminService.simulate(request));
    }
}
