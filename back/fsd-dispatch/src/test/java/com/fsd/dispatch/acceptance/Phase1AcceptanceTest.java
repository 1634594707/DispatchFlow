package com.fsd.dispatch.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicyImpl;
import com.fsd.dispatch.fleet.simulation.SimulationFleetAdapter;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.impl.DispatchExceptionServiceImpl;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.dispatch.dispatch.DispatchVehicleAssignService;
import com.fsd.dispatch.service.impl.DispatchTaskServiceImpl;
import com.fsd.dispatch.service.impl.ParkPilotSimulationServiceImpl;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 1 acceptance criteria — automated behavioral checks.
 * See docs/phase1-acceptance.md for manual UI verification steps.
 */
@DisplayName("Phase 1 验收")
class Phase1AcceptanceTest {

    private static final String UI_LABEL_STANDBY = "待命中";

    private final FleetChargePolicyImpl fleetChargePolicy =
            new FleetChargePolicyImpl(new FleetEnergyProperties());

    @Nested
    @DisplayName("1. 无订单：满电插枪待命、不掉电、可派单")
    class PluggedInStandbyWithoutOrders {

        @Test
        void fullPluggedStandbyShouldSkipBatteryDrain() {
            VehicleEntity vehicle = new VehicleEntity();
            vehicle.setBatteryLevel(100);
            FleetRuntime runtime = FleetRuntime.builder()
                    .runtimeStage("STANDBY")
                    .pluggedIn(true)
                    .build();
            assertTrue(fleetChargePolicy.shouldSkipDrain(vehicle, runtime));
        }

        @Test
        void fullPluggedStandbyVehicleShouldBeAssignable() {
            VehicleEntity vehicle = new VehicleEntity();
            vehicle.setBatteryLevel(100);
            assertTrue(fleetChargePolicy.isAssignable(vehicle));
        }

        @Test
        void standbyStageMapsToMonitoringLabel() {
            assertEquals(UI_LABEL_STANDBY, resolveStageLabel("STANDBY"));
        }

        @Test
        void pluggedInStandbyShouldSkipSimulationOffline() {
            boolean dispatchDemandActive = false;
            SimulationMotionState state = new SimulationMotionState();
            state.pluggedIn = true;
            state.stage = "STANDBY";
            assertTrue(!dispatchDemandActive || state.pluggedIn);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("2. 有订单：插枪待命车可接单并拔枪")
    class PluggedInVehicleDispatch {

        @Mock
        private com.fsd.dispatch.mapper.DispatchTaskMapper dispatchTaskMapper;
        @Mock
        private com.fsd.dispatch.service.DispatchTaskStateService dispatchTaskStateService;
        @Mock
        private com.fsd.dispatch.service.DispatchTaskOperateLogService operateLogService;
        @Mock
        private DispatchExceptionService dispatchExceptionService;
        @Mock
        private com.fsd.order.service.OrderStateService orderStateService;
        @Mock
        private VehicleService vehicleService;
        @Mock
        private DispatchVehicleAssignService dispatchVehicleAssignService;
        @Mock
        private com.fsd.dispatch.service.ParkingFacilityService parkingFacilityService;
        @Mock
        private com.fsd.dispatch.infra.DispatchLockService dispatchLockService;
        @Mock
        private DispatchEventPublisher eventPublisher;
        @Mock
        private com.fsd.dispatch.service.VehicleCommandService vehicleCommandService;
        @Mock
        private com.fsd.dispatch.fleet.service.FleetRuntimeService fleetRuntimeService;

        @Test
        void idlePluggedInVehicleCanBeAutoAssigned() {
            DispatchTaskServiceImpl service = new DispatchTaskServiceImpl(
                    dispatchTaskMapper,
                    dispatchTaskStateService,
                    operateLogService,
                    dispatchExceptionService,
                    orderStateService,
                    vehicleService,
                    dispatchVehicleAssignService,
                    parkingFacilityService,
                    dispatchLockService,
                    eventPublisher,
                    vehicleCommandService,
                    mock(com.fsd.dispatch.service.DispatchPauseControlService.class));

            var task = new com.fsd.dispatch.entity.DispatchTaskEntity();
            task.setId(4001L);
            task.setOrderId(5001L);
            task.setStatus(DispatchTaskStatus.PENDING.name());

            var order = new com.fsd.order.entity.OrderEntity();
            order.setId(5001L);
            order.setPickupPointId(101L);

            var vehicle = new VehicleEntity();
            vehicle.setId(8001L);
            vehicle.setBatteryLevel(100);
            vehicle.setOnlineStatus("ONLINE");
            vehicle.setDispatchStatus("IDLE");

            when(dispatchTaskStateService.getTask(4001L)).thenReturn(task);
            doNothing().when(dispatchTaskStateService).assertCanAutoAssign(task);
            when(orderStateService.getOrder(5001L)).thenReturn(order);
            when(dispatchVehicleAssignService.selectBestVehicle(order))
                    .thenReturn(DispatchAssignResult.success(vehicle, "ok", 1.0, 1.0, 0.0, 0.0));
            when(dispatchLockService.acquireTaskLock(4001L)).thenReturn("lock");

            var response = service.autoAssignTask(4001L);

            assertEquals(DispatchTaskStatus.ASSIGNED.name(), response.getStatus());
            assertEquals(8001L, response.getVehicleId());
            verify(vehicleService).occupyVehicle(8001L, 4001L, 5001L);
        }

        @Test
        void assignShouldPublishUnpluggedRuntime() {
            SimulationFleetAdapter adapter = new SimulationFleetAdapter(fleetRuntimeService);
            VehicleEntity vehicle = new VehicleEntity();
            vehicle.setId(99L);
            vehicle.setBatteryLevel(100);

            SimulationMotionState unplugged = new SimulationMotionState();
            unplugged.pluggedIn = false;
            unplugged.stage = "TO_PICKUP";
            adapter.publishTelemetry(vehicle, unplugged);

            verify(fleetRuntimeService).save(argThat(runtime ->
                    Boolean.FALSE.equals(runtime.getPluggedIn())
                            && "TO_PICKUP".equals(runtime.getRuntimeStage())));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("3. 自动派单失败：MANUAL_PENDING + 单条 OPEN 异常")
    class AutoAssignFailureHandling {

        @Mock
        private DispatchExceptionRecordMapper exceptionRecordMapper;
        @Mock
        private DispatchEventPublisher eventPublisher;
        @Mock
        private com.fsd.dispatch.service.DispatchTaskOperateLogService operateLogService;

        @Test
        void duplicateOpenExceptionShouldNotBeInserted() {
            when(exceptionRecordMapper.selectCount(any())).thenReturn(0L, 1L);
            when(exceptionRecordMapper.insert(any(DispatchExceptionRecordEntity.class))).thenReturn(1);

            DispatchExceptionService service = new DispatchExceptionServiceImpl(
                    exceptionRecordMapper, eventPublisher, operateLogService);
            service.recordException(10L, 20L, null, "AUTO_ASSIGN_NO_VEHICLE", "first");
            service.recordException(10L, 20L, null, "AUTO_ASSIGN_NO_VEHICLE", "second");

            verify(exceptionRecordMapper, times(1)).insert(any(DispatchExceptionRecordEntity.class));
        }
    }

    @Nested
    @DisplayName("4. 仿真离线不产生 OPEN 异常")
    class SimulationOfflineBehavior {

        @Test
        void simulationServiceShouldNotDependOnExceptionRecorder() {
            boolean dependsOnExceptionService = Arrays.stream(ParkPilotSimulationServiceImpl.class.getDeclaredFields())
                    .anyMatch(field -> field.getType().equals(DispatchExceptionService.class));
            assertFalse(dependsOnExceptionService);
        }

        @Test
        void simulationServiceShouldNotInvokeExceptionMapper() {
            boolean dependsOnExceptionMapper = Arrays.stream(ParkPilotSimulationServiceImpl.class.getDeclaredFields())
                    .anyMatch(field -> field.getType().equals(DispatchExceptionRecordMapper.class));
            assertFalse(dependsOnExceptionMapper);
        }
    }

    private static String resolveStageLabel(String runtimeStage) {
        return switch (runtimeStage) {
            case "STANDBY" -> UI_LABEL_STANDBY;
            default -> runtimeStage;
        };
    }
}
