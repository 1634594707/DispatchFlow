package com.fsd.admin.service.impl;

import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final DispatchExceptionService dispatchExceptionService;
    private final VehicleService vehicleService;
    private final OrderMapper orderMapper;

    public AdminDashboardServiceImpl(DispatchAdminQueryService dispatchAdminQueryService,
                                     DispatchExceptionService dispatchExceptionService,
                                     VehicleService vehicleService,
                                     OrderMapper orderMapper) {
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.vehicleService = vehicleService;
        this.orderMapper = orderMapper;
    }

    @Override
    public AdminDashboardSummaryResponse getSummary(Long parkId) {
        DispatchSummaryResponse dispatchSummary = dispatchAdminQueryService.getSummary();
        VehicleSummaryResponse vehicleSummary = vehicleService.getSummary();
        long openExceptions = dispatchExceptionService.listOpenExceptions().stream()
                .filter(ex -> parkId == null || matchesPark(ex.getOrderId(), parkId))
                .count();
        return AdminDashboardSummaryResponse.builder()
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

    private boolean matchesPark(Long orderId, Long parkId) {
        if (orderId == null) {
            return false;
        }
        OrderEntity order = orderMapper.selectById(orderId);
        return order != null && parkId.equals(order.getParkId());
    }
}
