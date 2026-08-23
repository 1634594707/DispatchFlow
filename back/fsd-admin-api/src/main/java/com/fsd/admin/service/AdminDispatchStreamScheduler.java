package com.fsd.admin.service;

import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdminDispatchStreamScheduler {

    private final AdminDispatchStreamService streamService;
    private final AdminDashboardService dashboardService;
    private final DispatchAdminQueryService dispatchAdminQueryService;

    public AdminDispatchStreamScheduler(AdminDispatchStreamService streamService,
                                        AdminDashboardService dashboardService,
                                        DispatchAdminQueryService dispatchAdminQueryService) {
        this.streamService = streamService;
        this.dashboardService = dashboardService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
    }

    @Scheduled(fixedDelayString = "${fsd.dispatch.stream.interval-ms:3000}")
    public void pushSnapshots() {
        if (!streamService.hasClients()) {
            return;
        }
        for (Long parkId : streamService.getActiveParkIds()) {
            String eventTime = Instant.now().toString();
            AdminDashboardSummaryResponse summary = dashboardService.getSummary(parkId);
            Map<String, Object> dashboard = envelope("dispatch.dashboard.snapshot", parkId, eventTime);
            dashboard.put("pendingCount", summary.getPendingCount());
            dashboard.put("assigningCount", summary.getAssigningCount());
            dashboard.put("manualPendingCount", summary.getManualPendingCount());
            dashboard.put("executingCount", summary.getExecutingCount());
            dashboard.put("failedCount", summary.getFailedCount());
            dashboard.put("onlineVehicleCount", summary.getOnlineVehicleCount());
            dashboard.put("idleVehicleCount", summary.getIdleVehicleCount());
            dashboard.put("busyVehicleCount", summary.getBusyVehicleCount());
            dashboard.put("openExceptionCount", summary.getOpenExceptionCount());
            streamService.broadcast("dashboard", dashboard, parkId);

            DispatchInterventionQueueResponse intervention = dispatchAdminQueryService.getInterventionQueue(parkId);
            Map<String, Object> workbench = envelope("dispatch.workbench.snapshot", parkId, eventTime);
            workbench.put("pendingCount", intervention.getPendingCount());
            workbench.put("manualPendingCount", intervention.getManualPendingCount());
            workbench.put("openExceptionCount", intervention.getOpenExceptionCount());
            streamService.broadcast("workbench", workbench, parkId);
            streamService.broadcast("ping", envelope("dispatch.stream.ping", parkId, eventTime), parkId);
        }
    }

    private Map<String, Object> envelope(String eventType, Long parkId, String eventTime) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("businessKey", parkId == null ? "ALL_PARKS" : "PARK:" + parkId);
        payload.put("parkId", parkId);
        payload.put("eventTime", eventTime);
        payload.put("eventVersion", 1);
        payload.put("ts", eventTime);
        return payload;
    }
}
