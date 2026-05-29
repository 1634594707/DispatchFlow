package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleCommandStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.VehicleCommandEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.VehicleCommandMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleCommandServiceImplTest {

    @Mock
    private VehicleCommandMapper vehicleCommandMapper;
    @Mock
    private DispatchTaskMapper dispatchTaskMapper;
    @Mock
    private DispatchTaskStateService dispatchTaskStateService;
    @Mock
    private DispatchTaskOperateLogService operateLogService;
    @Mock
    private DispatchExceptionService dispatchExceptionService;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private ParkStationService parkStationService;
    @Mock
    private OrderStateService orderStateService;

    private VehicleCommandServiceImpl vehicleCommandService;

    @BeforeEach
    void setUp() {
        vehicleCommandService = new VehicleCommandServiceImpl(
                vehicleCommandMapper,
                dispatchTaskMapper,
                dispatchTaskStateService,
                operateLogService,
                dispatchExceptionService,
                vehicleService,
                parkStationService,
                orderStateService,
                new ObjectMapper());
    }

    @Test
    void issueDispatchCommandShouldPersistForRealVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(11L);
        vehicle.setVehicleCode("REAL-001");
        vehicle.setLinkMode(VehicleLinkMode.REAL.name());

        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(21L);
        task.setStatus(DispatchTaskStatus.ASSIGNED.name());

        OrderEntity order = new OrderEntity();
        order.setId(31L);
        order.setPickupPointId(101L);
        order.setDropoffPointId(201L);

        when(parkStationService.requireStation(101L)).thenReturn(station(101L, "A1"));
        when(parkStationService.requireStation(201L)).thenReturn(station(201L, "B1"));

        vehicleCommandService.issueDispatchCommandIfNeeded(vehicle, task, order);

        ArgumentCaptor<VehicleCommandEntity> captor = ArgumentCaptor.forClass(VehicleCommandEntity.class);
        verify(vehicleCommandMapper).insert(captor.capture());
        VehicleCommandEntity saved = captor.getValue();
        assertEquals(11L, saved.getVehicleId());
        assertEquals(VehicleCommandStatus.PENDING.name(), saved.getCommandStatus());
        assertNotNull(saved.getPayloadJson());
    }

    @Test
    void failCommandShouldMoveTaskToManualPendingAndRecordException() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(11L);
        vehicle.setVehicleCode("REAL-001");

        VehicleCommandEntity command = new VehicleCommandEntity();
        command.setId(99L);
        command.setVehicleId(11L);
        command.setTaskId(21L);
        command.setOrderId(31L);
        command.setCommandStatus(VehicleCommandStatus.DELIVERED.name());
        command.setPayloadJson("{\"taskId\":21}");

        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(21L);
        task.setOrderId(31L);
        task.setStatus(DispatchTaskStatus.ASSIGNED.name());

        when(vehicleService.getByVehicleCode("REAL-001")).thenReturn(vehicle);
        when(vehicleCommandMapper.selectById(99L)).thenReturn(command);
        when(dispatchTaskStateService.getTask(21L)).thenReturn(task);

        vehicleCommandService.failCommand("REAL-001", 99L, "route blocked");

        assertEquals(DispatchTaskStatus.MANUAL_PENDING.name(), task.getStatus());
        verify(dispatchExceptionService).recordException(21L, 31L, 11L, "COMMAND_FAILED", "route blocked");
        verify(orderStateService).revertToWaitingDispatch(31L);
        verify(vehicleService).releaseVehicle(11L, "IDLE");
    }

    private ParkStationResponse station(Long id, String code) {
        return ParkStationResponse.builder()
                .stationId(id)
                .stationCode(code)
                .stationName(code)
                .x(java.math.BigDecimal.TEN)
                .y(java.math.BigDecimal.TEN)
                .build();
    }
}
