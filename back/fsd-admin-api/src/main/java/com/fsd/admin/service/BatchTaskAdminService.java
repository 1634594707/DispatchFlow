package com.fsd.admin.service;

import com.fsd.admin.dto.AdminBatchTaskRequest;
import com.fsd.admin.vo.AdminBatchTaskResultResponse;

public interface BatchTaskAdminService {

    AdminBatchTaskResultResponse batchAutoAssign(AdminBatchTaskRequest request, String operatorId, String operatorName);

    AdminBatchTaskResultResponse batchCancel(AdminBatchTaskRequest request, String operatorId, String operatorName);

    AdminBatchTaskResultResponse batchReassign(AdminBatchTaskRequest request, String operatorId, String operatorName);
}
