package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.bootstrap.FsdCoreApplication;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.dispatch.service.TrafficZoneControlService;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.order.service.OrderService;
import com.fsd.order.vo.OrderCreateResponse;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleReportService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = FsdCoreApplication.class)
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "fsd.park.simulation.enabled=false",
        "fsd.fleet.telemetry.scheduler-enabled=false",
        "fsd.report.mail.enabled=false",
        "fsd.peak-mode.cron-enabled=false"
})
class DispatchFlowIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DispatchTaskService dispatchTaskService;
    @Autowired
    private VehicleReportService vehicleReportService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private VehicleMapper vehicleMapper;

    @MockBean(name = "redisDispatchLockService")
    private DispatchLockService dispatchLockService;
    @MockBean(name = "redisDispatchReportIdempotencyService")
    private DispatchReportIdempotencyService dispatchReportIdempotencyService;
    @MockBean(name = "rabbitDispatchEventPublisher")
    private DispatchEventPublisher dispatchEventPublisher;
    @MockBean
    private AdminAuthService adminAuthService;
    @MockBean
    private AdminParkScopeService adminParkScopeService;
    @MockBean
    private FleetRuntimeService fleetRuntimeService;
    @MockBean
    private DispatchPauseControlService dispatchPauseControlService;
    @MockBean
    private PeakModeService peakModeService;
    @MockBean
    private TrafficZoneControlService trafficZoneControlService;

    @BeforeEach
    void setUp() {
        IntegrationTestSchema.recreateSchema(jdbcTemplate);
        IntegrationTestSchema.seedParkData(jdbcTemplate);
        when(dispatchLockService.acquireTaskLock(anyLong())).thenAnswer(invocation -> "lock-" + invocation.getArgument(0));
        doNothing().when(dispatchLockService).releaseTaskLock(anyLong(), any());
        when(dispatchReportIdempotencyService.markIfFirstReport(any())).thenReturn(true);
        when(fleetRuntimeService.get(anyLong())).thenReturn(Optional.empty());
        when(dispatchPauseControlService.isDispatchPaused(any())).thenReturn(false);
        when(dispatchPauseControlService.isGlobalDispatchPaused()).thenReturn(false);
        when(peakModeService.isPeakMode(any())).thenReturn(false);
        when(trafficZoneControlService.isPointInPausedZone(any(), any(), any())).thenReturn(false);
    }

    @Test
    void shouldCompleteMainFlowFromOrderToTaskSuccess() {
        insertVehicle("PARK-001", "Vehicle 1", "ONLINE", "IDLE", 220.0, 170.0);

        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setExternalOrderNo("EXT-001");
        orderRequest.setSourceType("MANUAL");
        orderRequest.setBizType("DELIVERY");
        orderRequest.setPickupPointId(101L);
        orderRequest.setDropoffPointId(201L);
        orderRequest.setPriority("P1");
        orderRequest.setRemark("integration");

        OrderCreateResponse orderResponse = orderService.createOrder(orderRequest);

        DispatchTaskCreateRequest taskRequest = new DispatchTaskCreateRequest();
        taskRequest.setOrderId(orderResponse.getOrderId());
        taskRequest.setDispatchType("AUTO");
        taskRequest.setRemark("integration");
        DispatchTaskCreateResponse taskResponse = dispatchTaskService.createTask(taskRequest);

        DispatchTaskAssignResponse assignResponse = dispatchTaskService.autoAssignTask(taskResponse.getTaskId());
        assertEquals("ASSIGNED", assignResponse.getStatus());

        VehicleReportRequest startRequest = buildReport("PARK-001", assignResponse.getTaskId(), orderResponse.getOrderId(), "START_EXECUTE");
        vehicleReportService.handleReport(startRequest);

        VehicleReportRequest successRequest = buildReport("PARK-001", assignResponse.getTaskId(), orderResponse.getOrderId(), "TASK_SUCCESS");
        vehicleReportService.handleReport(successRequest);

        assertEquals("COMPLETED", orderMapper.selectById(orderResponse.getOrderId()).getStatus());
        VehicleEntity vehicleEntity = vehicleMapper.selectById(assignResponse.getVehicleId());
        assertEquals("IDLE", vehicleEntity.getDispatchStatus());
        assertEquals("ONLINE", vehicleEntity.getOnlineStatus());
    }

    private VehicleReportRequest buildReport(String vehicleCode, Long taskId, Long orderId, String reportType) {
        VehicleReportRequest request = new VehicleReportRequest();
        request.setVehicleCode(vehicleCode);
        request.setOnlineStatus("ONLINE");
        request.setDispatchStatus("BUSY");
        request.setTaskId(taskId);
        request.setOrderId(orderId);
        request.setReportType(reportType);
        request.setReportTime(LocalDateTime.now());
        request.setBatteryLevel(90);
        return request;
    }

    private void insertVehicle(String vehicleCode,
                               String vehicleName,
                               String onlineStatus,
                               String dispatchStatus,
                               Double currentLongitude,
                               Double currentLatitude) {
        jdbcTemplate.update("""
                INSERT INTO t_vehicle (
                    vehicle_code, vehicle_name, vehicle_type, online_status, dispatch_status,
                    current_task_id, current_order_id, current_latitude, current_longitude,
                    battery_level, last_report_time, remark, version, deleted
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, NULL, NULL, 0, 0)
                """,
                vehicleCode, vehicleName, "CAR", onlineStatus, dispatchStatus, currentLatitude, currentLongitude, 100);
    }

}
