package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardSummaryResponse {

    private long pendingCount;

    private long assigningCount;

    private long manualPendingCount;

    private long executingCount;

    private long failedCount;

    private long onlineVehicleCount;

    private long idleVehicleCount;

    private long busyVehicleCount;
}
