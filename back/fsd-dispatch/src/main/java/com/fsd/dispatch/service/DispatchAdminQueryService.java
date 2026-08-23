package com.fsd.dispatch.service;

import com.fsd.common.model.PageResponse;
import com.fsd.dispatch.dto.DispatchTaskQueryRequest;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchWorkbenchResponse;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.TaskTimelineResponse;
import java.util.List;

public interface DispatchAdminQueryService {

    List<DispatchTaskListItemResponse> listTasks();

    default List<DispatchTaskListItemResponse> listTasks(Long parkId) {
        return listTasks();
    }

    PageResponse<DispatchTaskListItemResponse> queryTasks(DispatchTaskQueryRequest request);

    DispatchTaskDetailResponse getTaskDetail(Long taskId);

    /** 统一任务时间线读取模型（路线图 3.2）：订单创建→派车→回报→异常→重试/改派→终态。 */
    TaskTimelineResponse getTaskTimeline(Long taskId);

    List<DispatchExceptionListItemResponse> listExceptions();

    default List<DispatchExceptionListItemResponse> listExceptions(Long parkId) {
        return listExceptions();
    }

    DispatchInterventionQueueResponse getInterventionQueue();

    DispatchInterventionQueueResponse getInterventionQueue(Long parkId);

    DispatchSummaryResponse getSummary();

    DispatchSummaryResponse getSummary(Long parkId);

    DispatchWorkbenchResponse getWorkbench(Long parkId);
}
