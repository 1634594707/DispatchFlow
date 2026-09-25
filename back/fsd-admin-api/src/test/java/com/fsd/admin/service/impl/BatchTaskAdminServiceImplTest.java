package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.admin.dto.AdminBatchTaskRequest;
import com.fsd.admin.vo.AdminBatchTaskItemResult;
import com.fsd.admin.vo.AdminBatchTaskResultResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 2.2：批量操作统一结果契约 —— 总数/成功数/失败数/失败原因/可重试条目/操作时间。
 */
class BatchTaskAdminServiceImplTest {

    private DispatchTaskService dispatchTaskService;
    private DispatchTaskMapper dispatchTaskMapper;
    private com.fsd.order.service.OrderStateService orderStateService;
    private com.fsd.dispatch.dispatch.DispatchBatchAssignService batchAssignService;
    private com.fsd.dispatch.config.DispatchPolicyProperties policyProperties;
    private BatchTaskAdminServiceImpl batchService;

    @BeforeEach
    void setUp() {
        dispatchTaskService = mock(DispatchTaskService.class);
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        orderStateService = mock(com.fsd.order.service.OrderStateService.class);
        batchAssignService = mock(com.fsd.dispatch.dispatch.DispatchBatchAssignService.class);
        policyProperties = new com.fsd.dispatch.config.DispatchPolicyProperties();
        when(dispatchTaskMapper.selectById(anyLong())).thenReturn(new DispatchTaskEntity());
        batchService = new BatchTaskAdminServiceImpl(dispatchTaskService, dispatchTaskMapper,
                orderStateService, batchAssignService, policyProperties);
    }

    private AdminBatchTaskRequest request(List<Long> taskIds) {
        AdminBatchTaskRequest request = new AdminBatchTaskRequest();
        request.setTaskIds(taskIds);
        return request;
    }

    private DispatchTaskAssignResponse assigned(long taskId) {
        return DispatchTaskAssignResponse.builder()
                .taskId(taskId)
                .status("ASSIGNED")
                .vehicleId(9001L)
                .message("Auto assign success")
                .build();
    }

    private DispatchTaskAssignResponse conflictFailure(long taskId) {
        return DispatchTaskAssignResponse.builder()
                .taskId(taskId)
                .status("MANUAL_PENDING")
                .reasonCode("CONFLICT")
                .reasonMessage("车辆被其他任务占用")
                .build();
    }

    @Test
    void batchResultShouldExposeCountsOperationTimeAndRetryableEntries() {
        when(dispatchTaskService.autoAssignTask(1L)).thenReturn(assigned(1L));
        when(dispatchTaskService.autoAssignTask(2L)).thenReturn(conflictFailure(2L));
        when(dispatchTaskService.autoAssignTask(3L))
                .thenThrow(new BusinessException("DISPATCH_TASK_STATUS_INVALID", "状态不允许"));

        AdminBatchTaskResultResponse result =
                batchService.batchAutoAssign(request(List.of(1L, 2L, 3L)), "op-1", "调度员");

        assertEquals("AUTO_ASSIGN", result.getOperation());
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getFailureCount());
        assertNotNull(result.getOperatedAt());

        // CONFLICT 属瞬时失败可重试；状态拒绝不可重试
        assertEquals(List.of(2L), result.getRetryableTaskIds());
        AdminBatchTaskItemResult conflictItem = result.getResults().get(1);
        assertTrue(conflictItem.isRetryable());
        assertEquals("CONFLICT", conflictItem.getReasonCode());
        AdminBatchTaskItemResult stateItem = result.getResults().get(2);
        assertTrue(!stateItem.isRetryable());
        assertEquals("DISPATCH_TASK_STATUS_INVALID", stateItem.getReasonCode());
    }

    @Test
    void systemErrorShouldBeMarkedRetryable() {
        when(dispatchTaskService.autoAssignTask(7L)).thenThrow(new IllegalStateException("db down"));

        AdminBatchTaskResultResponse result =
                batchService.batchAutoAssign(request(List.of(7L)), "op-1", "调度员");

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(List.of(7L), result.getRetryableTaskIds());
        assertEquals("SYSTEM_ERROR", result.getResults().get(0).getReasonCode());
    }

    @Test
    void batchUnassignShouldReleaseTasksInsteadOfCancelling() {
        // remark 未填时为 null，stub 使用 any() 以匹配
        when(dispatchTaskService.unassignTask(anyLong(), anyString(), anyString(), any()))
                .thenReturn(DispatchTaskAssignResponse.builder().taskId(5L).status("PENDING").build());

        AdminBatchTaskResultResponse result =
                batchService.batchUnassign(request(List.of(5L)), "op-9", "调度员");

        assertEquals("UNASSIGN", result.getOperation());
        assertEquals(1, result.getSuccessCount());
        verify(dispatchTaskService).unassignTask(5L, "op-9", "调度员", null);
        // 人工接管不得走取消路径
        verify(dispatchTaskService, never()).cancelTask(anyLong(), anyString(), anyString(), anyString());
    }

    /** 开关关闭（默认）：批量自动派车必须与引入撮合前逐字同序，否则"灰度未放量即改行为"。 */
    @Test
    void batchMatchingOffKeepsSubmissionOrder() {
        when(dispatchTaskService.autoAssignTask(1L)).thenReturn(assigned(1L));
        when(dispatchTaskService.autoAssignTask(2L)).thenReturn(assigned(2L));

        batchService.batchAutoAssign(request(List.of(1L, 2L)), "op-1", "调度员");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(dispatchTaskService);
        order.verify(dispatchTaskService).autoAssignTask(1L);
        order.verify(dispatchTaskService).autoAssignTask(2L);
        verify(batchAssignService, never()).assignOrders(any());
    }

    /** 开关打开：提交顺序由矩阵给出，撮合只改顺序、不改每单的派车判据。 */
    @Test
    void batchMatchingOnSubmitsInMatrixOrder() {
        policyProperties.setBatchEnabled(true);
        stubTaskOrder(1L, 11L);
        stubTaskOrder(2L, 12L);
        java.util.Map<Long, String> plan = new java.util.LinkedHashMap<>();
        plan.put(12L, "ZJF-AV-01");
        plan.put(11L, "ZJF-AV-02");
        when(batchAssignService.assignOrders(any())).thenReturn(
                new com.fsd.dispatch.dispatch.DispatchBatchAssignService.BatchAssignOutcome(
                        "HUNGARIAN", 2, 2, 2, 215D, 500D, 285D, plan));
        when(dispatchTaskService.autoAssignTask(anyLong())).thenAnswer(
                invocation -> assigned(invocation.getArgument(0)));

        batchService.batchAutoAssign(request(List.of(1L, 2L)), "op-1", "调度员");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(dispatchTaskService);
        order.verify(dispatchTaskService).autoAssignTask(2L);
        order.verify(dispatchTaskService).autoAssignTask(1L);
    }

    /** 撮合失败不得把"批量自动派车"这个按钮打死：退成原顺序，仍逐单派。 */
    @Test
    void batchMatchingFailureFallsBackToSubmissionOrder() {
        policyProperties.setBatchEnabled(true);
        stubTaskOrder(1L, 11L);
        stubTaskOrder(2L, 12L);
        when(batchAssignService.assignOrders(any())).thenThrow(new IllegalStateException("matrix blew up"));
        when(dispatchTaskService.autoAssignTask(anyLong())).thenAnswer(
                invocation -> assigned(invocation.getArgument(0)));

        AdminBatchTaskResultResponse result =
                batchService.batchAutoAssign(request(List.of(1L, 2L)), "op-1", "调度员");

        assertEquals(2, result.getSuccessCount());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(dispatchTaskService);
        order.verify(dispatchTaskService).autoAssignTask(1L);
        order.verify(dispatchTaskService).autoAssignTask(2L);
    }

    private void stubTaskOrder(long taskId, long orderId) {
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setOrderId(orderId);
        when(dispatchTaskMapper.selectById(taskId)).thenReturn(task);
        com.fsd.order.entity.OrderEntity order = new com.fsd.order.entity.OrderEntity();
        order.setId(orderId);
        when(orderStateService.getOrder(orderId)).thenReturn(order);
    }
}
