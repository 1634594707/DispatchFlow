package com.fsd.admin.service;

import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import java.time.Instant;
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
        AdminDashboardSummaryResponse summary = dashboardService.getSummary(null);
        streamService.broadcast("dashboard", summary);

        DispatchInterventionQueueResponse intervention = dispatchAdminQueryService.getInterventionQueue();
        streamService.broadcast("workbench", Map.of(
                "ts", Instant.now().toString(),
                "pendingCount", intervention.getPendingCount(),
                "manualPendingCount", intervention.getManualPendingCount(),
                "openExceptionCount", intervention.getOpenExceptionCount()
        ));
        streamService.broadcast("ping", Map.of("ts", Instant.now().toString()));
    }
}
