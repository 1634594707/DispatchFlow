package com.fsd.dispatch.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchInterventionQueueResponse {

    private int pendingCount;

    private int manualPendingCount;

    private int openExceptionCount;

    private List<DispatchTaskListItemResponse> pendingTasks;

    private List<DispatchTaskListItemResponse> manualPendingTasks;

    private List<DispatchExceptionListItemResponse> openExceptions;
}
