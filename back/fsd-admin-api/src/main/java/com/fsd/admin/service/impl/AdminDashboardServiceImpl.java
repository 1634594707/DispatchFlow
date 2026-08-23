package com.fsd.admin.service.impl;

import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final DispatchExceptionService dispatchExceptionService;
    private final VehicleService vehicleService;
    private final AdminParkScopeService adminParkScopeService;

    public AdminDashboardServiceImpl(DispatchAdminQueryService dispatchAdminQueryService,
                                     DispatchExceptionService dispatchExceptionService,
                                     VehicleService vehicleService,
                                     AdminParkScopeService adminParkScopeService) {
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.vehicleService = vehicleService;
        this.adminParkScopeService = adminParkScopeService;
    }

    @Override
    public AdminDashboardSummaryResponse getSummary(Long parkId) {
        DispatchSummaryResponse dispatchSummary = dispatchAdminQueryService.getSummary(parkId);
        VehicleSummaryResponse vehicleSummary = vehicleService.getSummary(parkId);
        long openExceptions = dispatchExceptionService.listOpenExceptions().stream()
                .filter(ex -> adminParkScopeService.matchesOrder(ex.getOrderId(), parkId))
                .count();
        return AdminDashboardSummaryResponse.builder()
                .parkId(parkId)
                .pendingCount(dispatchSummary.getPendingCount())
                .assigningCount(dispatchSummary.getAssigningCount())
                .manualPendingCount(dispatchSummary.getManualPendingCount())
                .executingCount(dispatchSummary.getExecutingCount())
                .failedCount(dispatchSummary.getFailedCount())
                .onlineVehicleCount(vehicleSummary.getOnlineCount())
                .idleVehicleCount(vehicleSummary.getIdleCount())
                .busyVehicleCount(vehicleSummary.getBusyCount())
                .openExceptionCount(openExceptions)
                .build();
    }

}
