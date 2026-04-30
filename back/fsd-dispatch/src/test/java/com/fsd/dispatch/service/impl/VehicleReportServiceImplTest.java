package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private VehicleReportServiceImpl vehicleReportService;

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

        DispatchTaskEntity taskEntity = buildTask(3003L, 1003L, DispatchTaskStatus.EXECUTING.name());

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
    void handleDuplicateReportShouldReturnIgnoredMessage() {
        VehicleReportRequest request = buildRequest("TASK_SUCCESS", 3010L, 1010L, "V-010");
        when(reportIdempotencyService.markIfFirstReport(request)).thenReturn(false);

        VehicleReportResponse response = vehicleReportService.handleReport(request);

        assertEquals("Duplicate report ignored", response.getMessage());
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
