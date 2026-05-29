package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchWorkbenchResponse;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.util.List;

public interface DispatchAdminQueryService {

    List<DispatchTaskListItemResponse> listTasks();

    DispatchTaskDetailResponse getTaskDetail(Long taskId);

    List<DispatchExceptionListItemResponse> listExceptions();

    DispatchInterventionQueueResponse getInterventionQueue();

    DispatchSummaryResponse getSummary();

    DispatchWorkbenchResponse getWorkbench(Long parkId);
}
