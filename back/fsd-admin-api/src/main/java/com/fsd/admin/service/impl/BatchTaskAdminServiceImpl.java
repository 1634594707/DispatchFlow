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
        return executeBatch("AUTO_ASSIGN", request.getTaskIds(), (taskId) -> dispatchTaskService.autoAssignTask(taskId));
    }

    @Override
    public AdminBatchTaskResultResponse batchCancel(AdminBatchTaskRequest request,
                                                    String operatorId,
                                                    String operatorName) {
        return executeBatch("CANCEL", request.getTaskIds(), (taskId) ->
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
        return executeBatch("REASSIGN", request.getTaskIds(), (taskId) -> {
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
        // 人工接管：释放车辆并退回 PENDING 重新排队，而不是取消任务（对齐任务状态机契约）。
        return executeBatch("UNASSIGN", request.getTaskIds(), (taskId) ->
                dispatchTaskService.unassignTask(taskId, operatorId, operatorName, request.getRemark()));
    }

    /** 瞬时失败原因：允许整批重试（路线图 2.2 可重试条目契约）。 */
    private static final java.util.Set<String> RETRYABLE_REASON_CODES = java.util.Set.of(
            "NO_VEHICLE", "CONFLICT", "HUB_CAPACITY_FULL", "ROUTE_OCCUPANCY_FULL",
            "DISPATCH_TASK_LOCKED");

    private AdminBatchTaskResultResponse executeBatch(String operation,
                                                      List<Long> taskIds,
                                                      TaskAction action) {
        List<AdminBatchTaskItemResult> results = new ArrayList<>();
        List<Long> retryableTaskIds = new ArrayList<>();
        int success = 0;
        for (Long taskId : taskIds) {
            DispatchTaskEntity task = dispatchTaskMapper.selectById(taskId);
            String taskNo = task != null ? task.getTaskNo() : String.valueOf(taskId);
            try {
                DispatchTaskAssignResponse response = action.run(taskId);
                boolean ok = isSuccessResponse(operation, response);
                boolean retryable = !ok && response.getReasonCode() != null
                        && RETRYABLE_REASON_CODES.contains(response.getReasonCode());
                AdminBatchTaskItemResult item = AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(ok)
                        .status(response.getStatus())
                        .vehicleId(response.getVehicleId())
                        .reasonCode(response.getReasonCode())
                        .reasonMessage(response.getReasonMessage())
                        .suggestions(response.getSuggestions())
                        .retryable(retryable)
                        .message(ok ? response.getMessage() : firstNonBlank(response.getReasonMessage(), response.getMessage()))
                        .build();
                results.add(item);
                if (ok) {
                    success++;
                } else if (retryable) {
                    retryableTaskIds.add(taskId);
                }
            } catch (BusinessException ex) {
                boolean retryable = RETRYABLE_REASON_CODES.contains(ex.getCode());
                results.add(AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(false)
                        .reasonCode(ex.getCode())
                        .retryable(retryable)
                        .message(ex.getMessage())
                        .build());
                if (retryable) {
                    retryableTaskIds.add(taskId);
                }
            } catch (Exception ex) {
                // 非业务异常视为系统瞬时错误，可重试
                results.add(AdminBatchTaskItemResult.builder()
                        .taskId(taskId)
                        .taskNo(taskNo)
                        .success(false)
                        .reasonCode("SYSTEM_ERROR")
                        .retryable(true)
                        .message(ex.getMessage())
                        .build());
                retryableTaskIds.add(taskId);
            }
        }
        return AdminBatchTaskResultResponse.builder()
                .operation(operation)
                .total(taskIds.size())
                .successCount(success)
                .failureCount(taskIds.size() - success)
                .results(results)
                .retryableTaskIds(retryableTaskIds)
                .operatedAt(java.time.LocalDateTime.now())
                .build();
    }

    /** 按操作类型判定成功终态：派车/改派=ASSIGNED|EXECUTING，取消=CANCELLED，人工接管=PENDING。 */
    private boolean isSuccessResponse(String operation, DispatchTaskAssignResponse response) {
        if (response == null || response.getStatus() == null) {
            return false;
        }
        String status = response.getStatus();
        return switch (operation == null ? "" : operation) {
            case "CANCEL" -> DispatchTaskStatus.CANCELLED.name().equals(status);
            case "UNASSIGN" -> DispatchTaskStatus.PENDING.name().equals(status);
            default -> DispatchTaskStatus.ASSIGNED.name().equals(status)
                    || DispatchTaskStatus.EXECUTING.name().equals(status);
        };
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
