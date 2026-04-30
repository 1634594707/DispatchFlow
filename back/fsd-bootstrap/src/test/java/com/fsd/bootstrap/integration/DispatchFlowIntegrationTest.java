package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fsd.bootstrap.FsdCoreApplication;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.event.DispatchEventPublisher;
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

@SpringBootTest(classes = FsdCoreApplication.class)
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

    @BeforeEach
    void setUp() {
        recreateSchema();
        when(dispatchLockService.acquireTaskLock(anyLong())).thenAnswer(invocation -> "lock-" + invocation.getArgument(0));
        doNothing().when(dispatchLockService).releaseTaskLock(anyLong(), any());
        when(dispatchReportIdempotencyService.markIfFirstReport(any())).thenReturn(true);
    }

    @Test
    void shouldCompleteMainFlowFromOrderToTaskSuccess() {
        insertVehicle("VH-001", "Vehicle 1", "ONLINE", "IDLE", 220.0, 170.0);

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

        VehicleReportRequest startRequest = buildReport("VH-001", assignResponse.getTaskId(), orderResponse.getOrderId(), "START_EXECUTE");
        vehicleReportService.handleReport(startRequest);

        VehicleReportRequest successRequest = buildReport("VH-001", assignResponse.getTaskId(), orderResponse.getOrderId(), "TASK_SUCCESS");
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

    private void recreateSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_exception_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_task_operate_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_event_outbox");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_task");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_order");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_vehicle");

        jdbcTemplate.execute("""
                CREATE TABLE t_order (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(64) NOT NULL,
                    external_order_no VARCHAR(64),
                    source_type VARCHAR(32) NOT NULL,
                    biz_type VARCHAR(32) NOT NULL,
                    pickup_point_id BIGINT NOT NULL,
                    dropoff_point_id BIGINT NOT NULL,
                    priority VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    dispatch_task_id BIGINT,
                    remark VARCHAR(255),
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_no VARCHAR(64) NOT NULL,
                    order_id BIGINT NOT NULL,
                    vehicle_id BIGINT,
                    dispatch_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    fail_reason_code VARCHAR(64),
                    fail_reason_msg VARCHAR(255),
                    assign_time TIMESTAMP,
                    start_time TIMESTAMP,
                    finish_time TIMESTAMP,
                    manual_flag TINYINT DEFAULT 0,
                    retry_count INT DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_vehicle (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_code VARCHAR(64) NOT NULL,
                    vehicle_name VARCHAR(128) NOT NULL,
                    vehicle_type VARCHAR(32),
                    online_status VARCHAR(32) NOT NULL,
                    dispatch_status VARCHAR(32) NOT NULL,
                    current_task_id BIGINT,
                    current_order_id BIGINT,
                    current_latitude DECIMAL(10,6),
                    current_longitude DECIMAL(10,6),
                    battery_level INT,
                    last_report_time TIMESTAMP,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_task_operate_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT NOT NULL,
                    operate_type VARCHAR(32) NOT NULL,
                    before_status VARCHAR(32),
                    after_status VARCHAR(32),
                    operator_type VARCHAR(32) NOT NULL,
                    operator_id VARCHAR(64),
                    operator_name VARCHAR(64),
                    operate_remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_exception_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT,
                    order_id BIGINT,
                    vehicle_id BIGINT,
                    exception_type VARCHAR(32) NOT NULL,
                    exception_status VARCHAR(32) NOT NULL,
                    exception_msg VARCHAR(255),
                    occur_time TIMESTAMP NOT NULL,
                    resolved_time TIMESTAMP,
                    resolver_id VARCHAR(64),
                    resolve_remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_event_outbox (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    business_key VARCHAR(64) NOT NULL,
                    payload CLOB NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    last_error VARCHAR(255),
                    next_retry_time TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
