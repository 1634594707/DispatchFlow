package com.fsd.admin.service.impl;

import com.fsd.admin.dto.AdminBatchTaskRequest;
import com.fsd.admin.service.BatchTaskAdminService;
import com.fsd.admin.vo.AdminBatchTaskItemResult;
import com.fsd.admin.vo.AdminBatchTaskResultResponse;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchTaskAdminServiceImpl implements BatchTaskAdminService {

    private final DispatchTaskService dispatchTaskService;
    private final DispatchTaskMapper dispatchTaskMapper;

    public BatchTaskAdminServiceImpl(DispatchTaskService dispatchTaskService,
                                     DispatchTaskMapper dispatchTaskMapper) {
        this.dispatchTaskService = dispatchTaskService;
        this.dispatchTaskMapper = dispatchTaskMapper;
    }

    @Override
    public AdminBatchTaskResultResponse batchAutoAssign(AdminBatchTaskRequest request,
                                                        String operatorId,
                                                        String operatorName) {
        return executeBatch(request.getTaskIds(), (taskId) -> dispatchTaskService.autoAssignTask(taskId));
    }

    @Override
    public AdminBatchTaskResultResponse batchCancel(AdminBatchTaskRequest request,
                                                    String operatorId,
                                                    String operatorName) {
        return executeBatch(request.getTaskIds(), (taskId) ->
                dispatchTaskService.cancelTask(taskId, operatorId, operatorName, request.getRemark()));
    }

    @Override
    public AdminBatchTaskResultResponse batchReassign(AdminBatchTaskRequest request,
                                                      String operatorId,
                                                      String operatorName) {
        if (request.getVehicleId() == null) {
            throw new BusinessException("BATCH_VEHICLE_REQUIRED", "批量改派需指定车辆");
        }
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(request.getVehicleId());
        assignRequest.setOperatorId(operatorId);
        assignRequest.setOperatorName(operatorName);
        assignRequest.setRemark(request.getRemark());
        return executeBatch(request.getTaskIds(), (taskId) -> {
            DispatchTaskEntity task = dispatchTaskMapper.selectById(taskId);
            if (task != null && "ASSIGNED".equals(task.getStatus())) {
                return dispatchTaskService.reassignTask(taskId, assignRequest);
            }
            return dispatchTaskService.manualAssignTask(taskId, assignRequest);
        });
    }

    @Override
    public AdminBatchTaskResultResponse batchUnassign(AdminBatchTaskRequest request,
                                                      String operatorId,
                                                      String operatorName) {
        return executeBatch(request.getTaskIds(), (taskId) ->
                dispatchTaskService.cancelTask(taskId, operatorId, operatorName, request.getRemark()));
    }

    private AdminBatchTaskResultResponse executeBatch(List<Long> taskIds,
                                                      TaskAction action) {
        List<AdminBatchTaskItemResult> results = new ArrayList<>();
        int success = 0;
        for (Long taskId : taskIds) {
            DispatchTaskEntity task = dispatchTaskMapper.selectById(taskId);
            String taskNo = task != null ? task.getTaskNo() : String.valueOf(taskId);
            try {
                DispatchTaskAssignResponse response = action.run(taskId);
                boolean ok = isSuccessResponse(response);
                AdminBatchTaskItemResult item = AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(ok)
                        .status(response.getStatus())
                        .vehicleId(response.getVehicleId())
                        .reasonCode(response.getReasonCode())
                        .reasonMessage(response.getReasonMessage())
                        .suggestions(response.getSuggestions())
                        .message(ok ? response.getMessage() : firstNonBlank(response.getReasonMessage(), response.getMessage()))
                        .build();
                results.add(item);
                if (ok) {
                    success++;
                }
            } catch (BusinessException ex) {
                results.add(AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(false)
                        .message(ex.getMessage())
                        .build());
            } catch (Exception ex) {
                results.add(AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(false)
                        .message(ex.getMessage())
                        .build());
            }
        }
        return AdminBatchTaskResultResponse.builder()
                .total(taskIds.size())
                .successCount(success)
                .failureCount(taskIds.size() - success)
                .results(results)
                .build();
    }

    private boolean isSuccessResponse(DispatchTaskAssignResponse response) {
        if (response == null || response.getStatus() == null) {
            return false;
        }
        return DispatchTaskStatus.ASSIGNED.name().equals(response.getStatus())
                || DispatchTaskStatus.EXECUTING.name().equals(response.getStatus());
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    @FunctionalInterface
    private interface TaskAction {
        DispatchTaskAssignResponse run(Long taskId);
    }
}
