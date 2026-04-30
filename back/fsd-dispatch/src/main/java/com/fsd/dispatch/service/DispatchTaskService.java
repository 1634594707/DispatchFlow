package com.fsd.dispatch.service;

import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.util.List;

public interface DispatchTaskService {

    DispatchTaskCreateResponse createTask(DispatchTaskCreateRequest request);

    DispatchTaskAssignResponse autoAssignTask(Long taskId);

    DispatchTaskAssignResponse manualAssignTask(Long taskId, DispatchTaskManualAssignRequest request);

    DispatchTaskDetailResponse getTaskDetail(Long taskId);

    List<DispatchTaskListItemResponse> listManualPendingTasks();

    DispatchSummaryResponse getSummary();
}
