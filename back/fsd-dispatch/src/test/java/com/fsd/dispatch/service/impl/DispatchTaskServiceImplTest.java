package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchTaskServiceImplTest {

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
    private VehicleService vehicleService;
    @Mock
    private ParkPilotService parkPilotService;
    @Mock
    private DispatchLockService dispatchLockService;
    @Mock
    private DispatchEventPublisher eventPublisher;

    @InjectMocks
    private DispatchTaskServiceImpl dispatchTaskService;

    @Test
    void createTaskShouldPersistPendingTask() {
        DispatchTaskCreateRequest request = new DispatchTaskCreateRequest();
        request.setOrderId(1001L);
        request.setDispatchType("AUTO");
        request.setRemark("new task");

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(1001L);
        orderEntity.setStatus("WAITING_DISPATCH");
        when(orderStateService.getOrder(1001L)).thenReturn(orderEntity);
        doNothing().when(dispatchTaskStateService).assertCanCreateTask(1001L);
        when(dispatchTaskMapper.insert(any(DispatchTaskEntity.class))).thenAnswer(invocation -> {
            DispatchTaskEntity entity = invocation.getArgument(0);
            entity.setId(2001L);
            return 1;
        });

        DispatchTaskCreateResponse response = dispatchTaskService.createTask(request);

        assertEquals(2001L, response.getTaskId());
        assertEquals(DispatchTaskStatus.PENDING.name(), response.getStatus());
        verify(operateLogService).record(eq(2001L), eq("CREATE_TASK"), eq(null),
                eq(DispatchTaskStatus.PENDING.name()), eq("SYSTEM"), eq("system"), eq("system"), eq("new task"));
    }

    @Test
    void autoAssignShouldAssignNearestAvailableVehicle() {
        DispatchTaskEntity taskEntity = new DispatchTaskEntity();
        taskEntity.setId(3001L);
        taskEntity.setOrderId(1001L);
        taskEntity.setStatus(DispatchTaskStatus.PENDING.name());

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(1001L);
        orderEntity.setPickupPointId(501L);

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9001L);

        when(dispatchTaskStateService.getTask(3001L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanAutoAssign(taskEntity);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(vehicleEntity));
        when(orderStateService.getOrder(1001L)).thenReturn(orderEntity);
        when(parkPilotService.selectNearestVehicle(List.of(vehicleEntity), 501L)).thenReturn(vehicleEntity);
        when(dispatchLockService.acquireTaskLock(3001L)).thenReturn("lock-1");

        DispatchTaskAssignResponse response = dispatchTaskService.autoAssignTask(3001L);

        assertEquals(DispatchTaskStatus.ASSIGNED.name(), response.getStatus());
        assertEquals(9001L, response.getVehicleId());
        verify(vehicleService).occupyVehicle(9001L, 3001L, 1001L);
        verify(orderStateService).markDispatched(1001L, 3001L);
        verify(dispatchExceptionService, never()).recordException(any(), any(), any(), any(), any());
        verify(dispatchLockService).releaseTaskLock(3001L, "lock-1");
    }

    @Test
    void autoAssignShouldMoveToManualPendingWhenNoVehicleExists() {
        DispatchTaskEntity taskEntity = new DispatchTaskEntity();
        taskEntity.setId(3002L);
        taskEntity.setOrderId(1002L);
        taskEntity.setStatus(DispatchTaskStatus.PENDING.name());
        taskEntity.setRetryCount(0);

        when(dispatchTaskStateService.getTask(3002L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanAutoAssign(taskEntity);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of());
        when(dispatchLockService.acquireTaskLock(3002L)).thenReturn("lock-2");

        DispatchTaskAssignResponse response = dispatchTaskService.autoAssignTask(3002L);

        assertEquals(DispatchTaskStatus.MANUAL_PENDING.name(), response.getStatus());
        assertNull(response.getVehicleId());
        verify(dispatchExceptionService).recordException(3002L, 1002L, null,
                "AUTO_ASSIGN_NO_VEHICLE", "No assignable vehicle found");
        verify(orderStateService, never()).markDispatched(any(), any());
        verify(dispatchLockService).releaseTaskLock(3002L, "lock-2");
    }

    @Test
    void autoAssignShouldMoveToManualPendingWhenPickupPointIsInvalid() {
        DispatchTaskEntity taskEntity = new DispatchTaskEntity();
        taskEntity.setId(3004L);
        taskEntity.setOrderId(1004L);
        taskEntity.setStatus(DispatchTaskStatus.PENDING.name());
        taskEntity.setRetryCount(0);

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(1004L);
        orderEntity.setPickupPointId(999L);

        VehicleEntity vehicleEntity = new VehicleEntity();
        vehicleEntity.setId(9004L);

        when(dispatchTaskStateService.getTask(3004L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanAutoAssign(taskEntity);
        when(vehicleService.listAssignableVehicles()).thenReturn(List.of(vehicleEntity));
        when(orderStateService.getOrder(1004L)).thenReturn(orderEntity);
        when(parkPilotService.selectNearestVehicle(List.of(vehicleEntity), 999L))
                .thenThrow(new com.fsd.common.exception.BusinessException("PARK_STATION_NOT_FOUND", "Park station not found"));
        when(dispatchLockService.acquireTaskLock(3004L)).thenReturn("lock-4");

        DispatchTaskAssignResponse response = dispatchTaskService.autoAssignTask(3004L);

        assertEquals(DispatchTaskStatus.MANUAL_PENDING.name(), response.getStatus());
        verify(dispatchExceptionService).recordException(3004L, 1004L, null,
                "AUTO_ASSIGN_POINT_INVALID", "Park station not found");
        verify(dispatchLockService).releaseTaskLock(3004L, "lock-4");
    }

    @Test
    void manualAssignShouldBindSpecifiedVehicle() {
        DispatchTaskEntity taskEntity = new DispatchTaskEntity();
        taskEntity.setId(3003L);
        taskEntity.setOrderId(1003L);
        taskEntity.setStatus(DispatchTaskStatus.MANUAL_PENDING.name());
        taskEntity.setRetryCount(1);

        DispatchTaskManualAssignRequest request = new DispatchTaskManualAssignRequest();
        request.setVehicleId(9002L);
        request.setOperatorId("u1");
        request.setOperatorName("dispatcher");
        request.setRemark("manual");

        when(dispatchTaskStateService.getTask(3003L)).thenReturn(taskEntity);
        doNothing().when(dispatchTaskStateService).assertCanManualAssign(taskEntity);
        when(dispatchLockService.acquireTaskLock(3003L)).thenReturn("lock-3");

        DispatchTaskAssignResponse response = dispatchTaskService.manualAssignTask(3003L, request);

        assertEquals(DispatchTaskStatus.ASSIGNED.name(), response.getStatus());
        assertEquals(9002L, response.getVehicleId());
        verify(vehicleService).occupyVehicle(9002L, 3003L, 1003L);
        verify(orderStateService).markDispatched(1003L, 3003L);
        verify(dispatchLockService).releaseTaskLock(3003L, "lock-3");
    }
}
