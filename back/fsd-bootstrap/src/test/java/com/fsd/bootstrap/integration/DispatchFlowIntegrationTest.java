package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
        // 车辆停放在 A1 取货站（GPS 与 schematic 双坐标对齐，确保 Phase 4 haversine 距离计算有效）
        insertVehicle("ZJF-AV-01", "Vehicle 1", "ONLINE", "IDLE", 121.0744, 31.9604);

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

        // §7.3 的端到端契约：任一历史任务要能被事后追问「为什么是这台车、与次优差多少分」。
        // 这条断言以前写不了 —— 集成夹具的 H2 schema 早于 V54，快照写入一直失败并降级成一条 WARN，
        // 于是"决策可证明性"在这套测试里其实是空缺的（§13.15 记的同一族问题：降级没有信号）。
        Long snapshotRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dispatch_decision_snapshot WHERE order_id = ?",
                Long.class, orderResponse.getOrderId());
        assertEquals(1L, snapshotRows.longValue(), "一次派单留一行快照");
        String snapshotWinner = jdbcTemplate.queryForObject(
                "SELECT winner_vehicle_code FROM t_dispatch_decision_snapshot WHERE order_id = ?",
                String.class, orderResponse.getOrderId());
        assertEquals("ZJF-AV-01", snapshotWinner);
        BigDecimal snapshotGap = jdbcTemplate.queryForObject(
                "SELECT score_gap FROM t_dispatch_decision_snapshot WHERE order_id = ?",
                BigDecimal.class, orderResponse.getOrderId());
        assertNotNull(snapshotGap, "分差必须落库，否则快照回答不了\"换成次优会差多少\"");
        String snapshotPolicy = jdbcTemplate.queryForObject(
                "SELECT policy_id FROM t_dispatch_decision_snapshot WHERE order_id = ?",
                String.class, orderResponse.getOrderId());
        assertNotNull(snapshotPolicy, "必须能回溯到当时命中的决策策略与版本");

        VehicleReportRequest startRequest = buildReport("ZJF-AV-01", assignResponse.getTaskId(), orderResponse.getOrderId(), "START_EXECUTE");
        vehicleReportService.handleReport(startRequest);

        VehicleReportRequest successRequest = buildReport("ZJF-AV-01", assignResponse.getTaskId(), orderResponse.getOrderId(), "TASK_SUCCESS");
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
                    battery_level, last_report_time, remark, deleted
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, CURRENT_TIMESTAMP, NULL, 0)
                """,
                vehicleCode, vehicleName, "CAR", onlineStatus, dispatchStatus, currentLatitude, currentLongitude, 100);
    }

}
