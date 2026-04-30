package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.util.List;

public interface DispatchAdminQueryService {

    List<DispatchTaskListItemResponse> listTasks();

    DispatchTaskDetailResponse getTaskDetail(Long taskId);

    List<DispatchExceptionRecordEntity> listExceptions();

    DispatchSummaryResponse getSummary();
}
