package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleReportResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleReportServiceImplTest {

    @Mock
    private VehicleService vehicleService;
    @Mock
    private DispatchTaskMapper dispatchTaskMapper;
    @Mock
    private DispatchTaskStateService dispatchTaskStateService;
    @Mock
    private DispatchTaskOperateLogService operateLogService;
    @Mock
    private DispatchExceptionService dispatchExceptionService;
    @Mock
    private OrderStateService orderStateService;
    @Mock
    private DispatchReportIdempotencyService reportIdempotencyService;
    @Mock
    private DispatchEventPublisher eventPublisher;
    @Mock
    private com.fsd.dispatch.service.PeakModeService peakModeService;

    @InjectMocks
    private VehicleReportServiceImpl vehicleReportService;

    /** 状态迁移走带前置条件的 UPDATE：默认放行（影响 1 行），竞态用例单独改写返回 0。 */
    @org.junit.jupiter.api.BeforeEach
    void allowStatusGuardedUpdate() {
        org.mockito.Mockito.lenient().when(dispatchTaskMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void concurrentStateChangeShouldRejectReportInsteadOfClobbering() {
        VehicleReportRequest request = buildRequest("START_EXECUTE", 3020L, 1020L, "V-020");

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9020L);
        vehicleEntity.setVehicleCode("V-020");

        DispatchTaskEntity taskEntity = buildTask(3020L, 1020L, DispatchTaskStatus.ASSIGNED.name());

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenReturn(vehicleEntity);
        when(dispatchTaskStateService.getTask(3020L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanStartExecute(taskEntity);
        // 库里已被超时任务改成 FAILED：前置状态不再匹配 ⇒ 0 行
        when(dispatchTaskMapper.update(any(), any())).thenReturn(0);

        com.fsd.common.exception.BusinessException ex = org.junit.jupiter.api.Assertions
                .assertThrows(com.fsd.common.exception.BusinessException.class,
                        () -> vehicleReportService.handleReport(request));

        assertEquals("DISPATCH_TASK_STATE_CONFLICT", ex.getCode());
        // 幂等标记必须释放，车端重报才会被再次处理
        verify(reportIdempotencyService).releaseReport(request);
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleStartExecuteShouldPromoteTaskAndOrder() {
        VehicleReportRequest request = buildRequest("START_EXECUTE", 3001L, 1001L, "V-001");

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9001L);
        vehicleEntity.setVehicleCode("V-001");

        DispatchTaskEntity taskEntity = buildTask(3001L, 1001L, DispatchTaskStatus.ASSIGNED.name());

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenReturn(vehicleEntity);
        when(dispatchTaskStateService.getTask(3001L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanStartExecute(taskEntity);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals(DispatchTaskStatus.EXECUTING.name(), response.getTaskStatus());
        assertEquals("IN_PROGRESS", response.getOrderStatus());
        verify(orderStateService).markInProgress(1001L);

        // 守卫必须真的落进 WHERE：只验"调了 update"证明不了前置状态进了条件
        org.mockito.ArgumentCaptor<DispatchTaskEntity> entityCaptor =
                org.mockito.ArgumentCaptor.forClass(DispatchTaskEntity.class);
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<DispatchTaskEntity>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(dispatchTaskMapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        // 只验结构：走的是"实体 + 条件 Wrapper"的重载，且 SET 的是新状态。
        // Wrapper 内部内容在裸单测里不可观测——MP 到渲染 SQL 时才填 paramNameValuePairs（实测为空 Map），
        // 且 lambda 列名解析要 MyBatis 启动期的 TableInfo 缓存。WHERE 真身留给集成测试钉。
        assertEquals(DispatchTaskStatus.EXECUTING.name(), entityCaptor.getValue().getStatus(),
                "SET 的是新状态");
        verify(dispatchTaskMapper, org.mockito.Mockito.never())
                .updateById(org.mockito.ArgumentMatchers.any(DispatchTaskEntity.class));
    }

    @Test
    void handleTaskSuccessShouldCompleteAndReleaseVehicle() {
        VehicleReportRequest request = buildRequest("TASK_SUCCESS", 3002L, 1002L, "V-002");

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9002L);
        vehicleEntity.setVehicleCode("V-002");

        DispatchTaskEntity taskEntity = buildTask(3002L, 1002L, DispatchTaskStatus.EXECUTING.name());

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenReturn(vehicleEntity);
        when(dispatchTaskStateService.getTask(3002L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanFinish(taskEntity);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals(DispatchTaskStatus.SUCCESS.name(), response.getTaskStatus());
        assertEquals("COMPLETED", response.getOrderStatus());
        verify(orderStateService).markCompleted(1002L);
        verify(vehicleService).releaseVehicle(9002L, VehicleDispatchStatus.IDLE.name());
    }

    @Test
    void handleTaskFailedShouldMarkFailureAndRecordException() {
        VehicleReportRequest request = buildRequest("TASK_FAILED", 3003L, 1003L, "V-003");
        request.setResultCode("TASK_ERR");
        request.setResultMessage("vehicle blocked");

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9003L);
        vehicleEntity.setVehicleCode("V-003");

        // 重试次数已达上限，应直接标记为 FAILED
        DispatchTaskEntity taskEntity = buildTask(3003L, 1003L, DispatchTaskStatus.EXECUTING.name());
        taskEntity.setRetryCount(3);

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenReturn(vehicleEntity);
        when(dispatchTaskStateService.getTask(3003L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanFinish(taskEntity);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals(DispatchTaskStatus.FAILED.name(), response.getTaskStatus());
        assertEquals("FAILED", response.getOrderStatus());
        verify(orderStateService).markFailed(1003L, "vehicle blocked");
        verify(vehicleService).releaseVehicle(9003L, VehicleDispatchStatus.IDLE.name());
        verify(dispatchExceptionService).recordException(3003L, 1003L, 9003L,
                "TASK_EXECUTE_FAILED", "vehicle blocked");
    }

    @Test
    void handleTaskFailedShouldRetryWhenRetryCountBelowLimit() {
        VehicleReportRequest request = buildRequest("TASK_FAILED", 3005L, 1005L, "V-005");
        request.setResultCode("TASK_ERR");
        request.setResultMessage("transient error");

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9005L);
        vehicleEntity.setVehicleCode("V-005");

        // retryCount < 3，应重置为 PENDING 等待重试
        DispatchTaskEntity taskEntity = buildTask(3005L, 1005L, DispatchTaskStatus.EXECUTING.name());
        taskEntity.setRetryCount(1);

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenReturn(vehicleEntity);
        when(dispatchTaskStateService.getTask(3005L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanFinish(taskEntity);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals(DispatchTaskStatus.PENDING.name(), response.getTaskStatus());
        assertEquals("WAITING_DISPATCH", response.getOrderStatus());
        verify(vehicleService).releaseVehicle(9005L, VehicleDispatchStatus.IDLE.name());
        verify(dispatchExceptionService).recordException(3005L, 1005L, 9005L,
                "TASK_EXECUTE_FAILED_RETRY", "transient error");
    }

    @Test
    void handleDuplicateReportShouldReturnIgnoredMessage() {
        VehicleReportRequest request = buildRequest("TASK_SUCCESS", 3010L, 1010L, "V-010");
        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(false);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals("Duplicate report ignored", response.getMessage());
    }

    @Test
    void handleFailedReportShouldReleaseIdempotencyKey() {
        VehicleReportRequest request = buildRequest("START_EXECUTE", 3004L, 1004L, "V-004");

        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(true);
        when(vehicleService.updateSnapshot(request)).thenThrow(new RuntimeException("db down"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> vehicleReportService.handleReport(request));

        verify(reportIdempotencyService).releaseReport(request);
    }

    private VehicleReportRequest buildRequest(String reportType, Long taskId, Long orderId, String vehicleCode) {
        VehicleReportRequest request = new VehicleReportRequest();
        request.setVehicleCode(vehicleCode);
        request.setOnlineStatus("ONLINE");
        request.setDispatchStatus("BUSY");
        request.setTaskId(taskId);
        request.setOrderId(orderId);
        request.setReportType(reportType);
        request.setReportTime(LocalDateTime.now());
        return request;
    }

    private DispatchTaskEntity buildTask(Long taskId, Long orderId, String status) {
        DispatchTaskEntity taskEntity = new DispatchTaskEntity();
        taskEntity.setId(taskId);
        taskEntity.setOrderId(orderId);
        taskEntity.setStatus(status);
        return taskEntity;
    }
}
