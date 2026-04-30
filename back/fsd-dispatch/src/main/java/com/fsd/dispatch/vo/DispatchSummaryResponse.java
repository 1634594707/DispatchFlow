package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchSummaryResponse {

    private long pendingCount;

    private long assigningCount;

    private long manualPendingCount;

    private long executingCount;

    private long failedCount;
}
