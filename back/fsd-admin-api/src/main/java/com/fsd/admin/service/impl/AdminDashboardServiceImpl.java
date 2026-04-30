package com.fsd.admin.service.impl;

import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final VehicleService vehicleService;

    public AdminDashboardServiceImpl(DispatchAdminQueryService dispatchAdminQueryService,
                                     VehicleService vehicleService) {
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.vehicleService = vehicleService;
    }

    @Override
    public AdminDashboardSummaryResponse getSummary() {
        DispatchSummaryResponse dispatchSummary = dispatchAdminQueryService.getSummary();
        VehicleSummaryResponse vehicleSummary = vehicleService.getSummary();
        return AdminDashboardSummaryResponse.builder()
                .pendingCount(dispatchSummary.getPendingCount())
                .assigningCount(dispatchSummary.getAssigningCount())
                .manualPendingCount(dispatchSummary.getManualPendingCount())
                .executingCount(dispatchSummary.getExecutingCount())
                .failedCount(dispatchSummary.getFailedCount())
                .onlineVehicleCount(vehicleSummary.getOnlineCount())
                .idleVehicleCount(vehicleSummary.getIdleCount())
                .busyVehicleCount(vehicleSummary.getBusyCount())
                .build();
    }
}
