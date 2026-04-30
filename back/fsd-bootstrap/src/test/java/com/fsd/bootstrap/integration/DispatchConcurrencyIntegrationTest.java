package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fsd.bootstrap.FsdCoreApplication;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.service.OrderService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = FsdCoreApplication.class)
class DispatchConcurrencyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DispatchTaskService dispatchTaskService;
    @Autowired
    private DispatchTaskMapper dispatchTaskMapper;

    @MockBean(name = "redisDispatchLockService")
    private DispatchLockService dispatchLockService;
    @MockBean(name = "redisDispatchReportIdempotencyService")
    private DispatchReportIdempotencyService dispatchReportIdempotencyService;
    @MockBean(name = "rabbitDispatchEventPublisher")
    private DispatchEventPublisher dispatchEventPublisher;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        recreateSchema();
        executorService = Executors.newFixedThreadPool(2);
        when(dispatchLockService.acquireTaskLock(anyLong())).thenAnswer(invocation -> "lock-" + invocation.getArgument(0));
        doNothing().when(dispatchLockService).releaseTaskLock(anyLong(), any());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldAllowOnlyOneTaskToOccupySameVehicleUnderConcurrency() throws Exception {
        insertVehicle("VH-101", "Vehicle 101", "ONLINE", "IDLE");

        Long taskId1 = createPendingTask("EXT-C-1");
        Long taskId2 = createPendingTask("EXT-C-2");
        Long vehicleId = jdbcTemplate.queryForObject("SELECT id FROM t_vehicle WHERE vehicle_code = 'VH-101'", Long.class);

        DispatchTaskManualAssignRequest request = new DispatchTaskManualAssignRequest();
        request.setVehicleId(vehicleId);
        request.setOperatorId("dispatcher-1");
        request.setOperatorName("dispatcher-1");
        request.setRemark("concurrency");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> task1 = () -> invokeAssign(taskId1, request, ready, start);
        Callable<Boolean> task2 = () -> invokeAssign(taskId2, request, ready, start);

        List<Future<Boolean>> futures = new ArrayList<>();
        futures.add(executorService.submit(task1));
        futures.add(executorService.submit(task2));

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        int successCount = 0;
        int failedCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(10, TimeUnit.SECONDS)) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        assertEquals(1, successCount);
        assertEquals(1, failedCount);

        long assignedCount = dispatchTaskMapper.selectList(null).stream()
                .filter(task -> "ASSIGNED".equals(task.getStatus()))
                .count();
        assertEquals(1L, assignedCount);

        String dispatchStatus = jdbcTemplate.queryForObject("SELECT dispatch_status FROM t_vehicle WHERE id = ?", String.class, vehicleId);
        assertEquals("BUSY", dispatchStatus);
    }

    private boolean invokeAssign(Long taskId, DispatchTaskManualAssignRequest request,
                                 CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            dispatchTaskService.manualAssignTask(taskId, request);
            return true;
        } catch (BusinessException ex) {
            assertTrue("VEHICLE_NOT_ASSIGNABLE".equals(ex.getCode()) || "DISPATCH_TASK_LOCKED".equals(ex.getCode()));
            return false;
        }
    }

    private Long createPendingTask(String externalOrderNo) {
        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setExternalOrderNo(externalOrderNo);
        orderRequest.setSourceType("MANUAL");
        orderRequest.setBizType("DELIVERY");
        orderRequest.setPickupPointId(11L);
        orderRequest.setDropoffPointId(22L);
        orderRequest.setPriority("P1");

        Long orderId = orderService.createOrder(orderRequest).getOrderId();

        DispatchTaskCreateRequest taskRequest = new DispatchTaskCreateRequest();
        taskRequest.setOrderId(orderId);
        taskRequest.setDispatchType("MANUAL");
        return dispatchTaskService.createTask(taskRequest).getTaskId();
    }

    private void insertVehicle(String vehicleCode, String vehicleName, String onlineStatus, String dispatchStatus) {
        jdbcTemplate.update("""
                INSERT INTO t_vehicle (
                    vehicle_code, vehicle_name, vehicle_type, online_status, dispatch_status,
                    current_task_id, current_order_id, current_latitude, current_longitude,
                    battery_level, last_report_time, remark, version, deleted
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, ?, NULL, NULL, 0, 0)
                """,
                vehicleCode, vehicleName, "CAR", onlineStatus, dispatchStatus, 100);
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
