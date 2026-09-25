package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.bootstrap.FsdCoreApplication;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.service.OrderService;
import com.fsd.vehicle.dto.VehicleReportRequest;
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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = FsdCoreApplication.class)
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "fsd.park.simulation.enabled=false",
        "fsd.fleet.telemetry.scheduler-enabled=false",
        "fsd.report.mail.enabled=false",
        "fsd.peak-mode.cron-enabled=false"
})
class DispatchConcurrencyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DispatchTaskService dispatchTaskService;
    @Autowired
    private DispatchTaskMapper dispatchTaskMapper;
    @Autowired
    private com.fsd.vehicle.service.VehicleReportService vehicleReportService;

    @MockBean(name = "redisDispatchLockService")
    private DispatchLockService dispatchLockService;
    @MockBean(name = "redisDispatchReportIdempotencyService")
    private DispatchReportIdempotencyService dispatchReportIdempotencyService;
    @MockBean(name = "rabbitDispatchEventPublisher")
    private DispatchEventPublisher dispatchEventPublisher;
    @MockBean
    private DispatchPauseControlService dispatchPauseControlService;
    @MockBean
    private AdminAuthService adminAuthService;
    @MockBean
    private AdminParkScopeService adminParkScopeService;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        IntegrationTestSchema.recreateSchema(jdbcTemplate);
        IntegrationTestSchema.seedParkData(jdbcTemplate);
        executorService = Executors.newFixedThreadPool(2);
        when(dispatchLockService.acquireTaskLock(anyLong())).thenAnswer(invocation -> "lock-" + invocation.getArgument(0));
        doNothing().when(dispatchLockService).releaseTaskLock(anyLong(), any());
        when(dispatchPauseControlService.isDispatchPaused(any())).thenReturn(false);
        when(dispatchPauseControlService.isGlobalDispatchPaused()).thenReturn(false);
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

    /**
     * 同一性质走**服务入口**再钉一遍：已提交的状态迁移之后，迟到的上报不得把终态改写回去。
     *
     * <p>上报路径不持任务锁（§13.41 的结论），所以这条链上挡得住的只有"前置状态进 WHERE"
     * 或它前面的状态守卫 —— 两者任一生效都行，但绝不允许静默覆盖。
     */
    @Test
    void lateReportCannotOverwriteCommittedTransition() {
        insertVehicle("VH-LATE-1", "Late Vehicle", "ONLINE", "IDLE");
        Long taskId = createPendingTask("EXT-LATE-1");
        Long vehicleId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_vehicle WHERE vehicle_code = 'VH-LATE-1'", Long.class);
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(vehicleId);
        assignRequest.setOperatorId("dispatcher-late");
        assignRequest.setOperatorName("dispatcher-late");
        dispatchTaskService.manualAssignTask(taskId, assignRequest);
        when(dispatchReportIdempotencyService.markIfFirstReport(any())).thenReturn(true);

        vehicleReportService.handleReport(report(taskId, "VH-LATE-1", "START_EXECUTE"));
        assertEquals("EXECUTING", taskStatus(taskId));

        vehicleReportService.handleReport(report(taskId, "VH-LATE-1", "TASK_SUCCESS"));
        assertEquals("SUCCESS", taskStatus(taskId));

        String outcome;
        try {
            vehicleReportService.handleReport(report(taskId, "VH-LATE-1", "TASK_FAILED"));
            outcome = "OK";
        } catch (BusinessException ex) {
            outcome = ex.getCode();
        }
        assertTrue("DISPATCH_TASK_STATUS_INVALID".equals(outcome)
                        || "DISPATCH_TASK_STATE_CONFLICT".equals(outcome),
                "迟到的失败上报必须被拒，实际 " + outcome);
        assertEquals("SUCCESS", taskStatus(taskId), "终态不得被迟到上报改写");
    }

    /**
     * §7.2 待办 (b)：把"读到旧状态 → 期间别人提交 → 我这写必须落空"这段真 SQL 钉住。
     *
     * <p>裸单测里这条不可观测（MP 到渲染期才填 {@code paramNameValuePairs}，实测为空 Map；lambda
     * 列名解析还要启动期的 {@code TableInfo} 缓存，实测抛 "can not find lambda cache for this entity"）
     * ⇒ 走完整 Spring 上下文 + H2：0 行 ⇒ 生产代码 {@code updateIfStatusUnchanged} 抛
     * {@code DISPATCH_TASK_STATE_CONFLICT}（那半由 {@code VehicleReportServiceImplTest} 钉）。
     *
     * <p><b>为什么是确定性交错而不是两个线程真撞</b>：实测 H2 的 {@code UPDATE} 拿到行锁后
     * <b>不重评</b> WHERE，两条并发写都会返回 1 行（跑出 {@code [OK, OK]}），那是引擎差异不是产品缺陷；
     * 拿它当断言会得到"H2 永远绿、MySQL 才会红"的反向假绿。MySQL InnoDB 会重评 ⇒ 后到方 0 行，
     * 该行为留待部署环境验证（路线图 §7.2 已记这条边界）。
     */
    @Test
    void conditionalUpdateMustMissRowThatChangedAfterItWasRead() {
        insertVehicle("VH-STALE-1", "Stale Vehicle", "ONLINE", "IDLE");
        Long taskId = createPendingTask("EXT-STALE-1");
        Long vehicleId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_vehicle WHERE vehicle_code = 'VH-STALE-1'", Long.class);
        DispatchTaskManualAssignRequest assignRequest = new DispatchTaskManualAssignRequest();
        assignRequest.setVehicleId(vehicleId);
        assignRequest.setOperatorId("dispatcher-stale");
        assignRequest.setOperatorName("dispatcher-stale");
        dispatchTaskService.manualAssignTask(taskId, assignRequest);

        DispatchTaskEntity stale = dispatchTaskMapper.selectById(taskId);
        assertEquals("ASSIGNED", stale.getStatus());

        // 模拟另一个提交者在我们读之后改了状态（上报路径不持任务锁，这种交错真实存在）
        jdbcTemplate.update("UPDATE t_dispatch_task SET status = 'CANCELLED' WHERE id = ?", taskId);

        stale.setStatus(DispatchTaskStatus.EXECUTING.name());
        int rows = dispatchTaskMapper.update(stale, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getId, stale.getId())
                .eq(DispatchTaskEntity::getStatus, "ASSIGNED"));

        assertEquals(0, rows, "前置状态写进 WHERE 后，这次写必须落空，不能把 CANCELLED 盖回 EXECUTING");
        assertEquals("CANCELLED", taskStatus(taskId));
    }

    private VehicleReportRequest report(Long taskId, String vehicleCode, String reportType) {
        VehicleReportRequest report = new VehicleReportRequest();
        report.setVehicleCode(vehicleCode);
        report.setOnlineStatus("ONLINE");
        report.setDispatchStatus("BUSY");
        report.setTaskId(taskId);
        report.setReportType(reportType);
        report.setReportTime(java.time.LocalDateTime.now());
        report.setBatteryLevel(80);
        report.setResultCode("LATE_REPORT");
        report.setResultMessage("late report probe");
        return report;
    }

    private String taskStatus(Long taskId) {
        return jdbcTemplate.queryForObject("SELECT status FROM t_dispatch_task WHERE id = ?", String.class, taskId);
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
                    battery_level, last_report_time, remark, deleted
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, ?, NULL, NULL, 0)
                """,
                vehicleCode, vehicleName, "CAR", onlineStatus, dispatchStatus, 100);
    }

}
