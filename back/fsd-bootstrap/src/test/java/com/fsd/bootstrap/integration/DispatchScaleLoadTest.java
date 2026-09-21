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
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.dispatch.service.TrafficZoneControlService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.service.OrderService;
import com.fsd.order.vo.OrderCreateResponse;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleReportService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 规模承载压测：100 台车 / 500 笔订单 / 1000 节点路网（可缩放）。
 *
 * <p>与 {@link DispatchFlowIntegrationTest} 使用同一套真 Spring 上下文 + H2 内存库，
 * 走真实链路：下单 → 建任务 → 多目标选车（含全链路 SOC 校验 + A* 路网可达性 + MAPF 预约）→
 * 执行上报 → 任务完成 → 车辆释放。因此压测数字可直接引用。
 *
 * <p><b>Redis 模式自动切换（T-04）</b>：MAPF 时空预约表在生产由 Redis 承载。
 * {@link RedisUnavailableCondition} 在上下文启动时探测 {@code spring.data.redis.host/port}
 * （默认 {@code localhost:6379}），据此二选一：
 * <ul>
 *   <li><b>REAL</b>——真实 Redis 可达：使用真实现，压测延迟包含 Redis 网络往返，
 *       并额外输出 Redis 单次往返回合探针（SET+GET，微秒级）</li>
 *   <li><b>FAKE</b>——不可达：回退 {@link FakeRedisConfig} 的"带时间桶过期的内存 Redis"，
 *       MAPF 冲突检测与重规划逻辑完全保留，仅去掉网络 I/O，便于无 Redis 环境回归</li>
 * </ul>
 * 报告首段会标注本次实际生效的模式与端点，两套数字口径不同、不可混用。
 *
 * <p>规模可用系统属性缩放，便于日常回归：
 * <pre>
 *   -Ddispatch.scale.vehicles=20 -Ddispatch.scale.orders=50 -Ddispatch.scale.nodes=400
 * </pre>
 *
 * <p>压测报告写入 {@code target/scale-report/dispatch-scale-report-{real|fake}.md}
 * （含 Redis 模式、逐单延迟分位与失败原因分布）。
 */
@SpringBootTest(classes = FsdCoreApplication.class)
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "fsd.park.simulation.enabled=false",
        "fsd.fleet.telemetry.scheduler-enabled=false",
        "fsd.report.mail.enabled=false",
        "fsd.peak-mode.cron-enabled=false"
})
@Import(DispatchScaleLoadTest.FakeRedisConfig.class)
class DispatchScaleLoadTest {

    private static final int VEHICLES = Integer.getInteger("dispatch.scale.vehicles", 100);
    private static final int ORDERS = Integer.getInteger("dispatch.scale.orders", 500);
    /** 目标路网节点数，实际节点数 = GRID_COLUMNS × GRID_ROWS（向下取整对齐）。 */
    private static final int NETWORK_NODES = Integer.getInteger("dispatch.scale.nodes", 1000);

    private static final int GRID_COLUMNS = 40;
    private static final int GRID_ROWS = Math.max(1, NETWORK_NODES / GRID_COLUMNS);
    private static final int EFFECTIVE_NODES = GRID_COLUMNS * GRID_ROWS;
    private static final int GRID_SPACING = 30;
    private static final int GRID_ORIGIN = 10;

    private static final Long PICKUP_STATION_ID = 101L;
    private static final Long DROPOFF_STATION_ID = 201L;

    /** 派单 P95 上限（毫秒）。单园区、内存库、无外部 I/O 的收紧值。 */
    private static final long P95_BUDGET_MS = 2000L;
    /** 完成率下限。 */
    private static final double MIN_SUCCESS_RATE = 0.95D;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DispatchTaskService dispatchTaskService;
    @Autowired
    private VehicleReportService vehicleReportService;
    @Autowired
    private VehicleMapper vehicleMapper;
    /**
     * T-04：真实 Redis 可达时注入真实现，否则注入 {@link FakeRedisConfig} 的内存假实现。
     * MAPF 时空预约表在生产由 Redis 承载，因此本字段决定压测是否包含 Redis 网络往返。
     */
    @Autowired
    private StringRedisTemplate redisTemplate;

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
        when(dispatchLockService.acquireTaskLock(anyLong())).thenAnswer(inv -> "lock-" + inv.getArgument(0));
        doNothing().when(dispatchLockService).releaseTaskLock(anyLong(), any());
        when(dispatchReportIdempotencyService.markIfFirstReport(any())).thenReturn(true);
        when(fleetRuntimeService.get(anyLong())).thenReturn(Optional.empty());
        when(dispatchPauseControlService.isDispatchPaused(any())).thenReturn(false);
        when(dispatchPauseControlService.isGlobalDispatchPaused()).thenReturn(false);
        when(peakModeService.isPeakMode(any())).thenReturn(false);
        when(trafficZoneControlService.isPointInPausedZone(any(), any(), any())).thenReturn(false);
    }

    @Test
    void shouldSustainVehicleOrderScaleOnRoadNetwork() throws IOException {
        seedRoadNetwork();
        seedVehicles();

        List<Long> latencies = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        int assigned = 0;

        for (int index = 1; index <= ORDERS; index++) {
            long started = System.nanoTime();
            OrderCreateResponse order = orderService.createOrder(orderRequest(index));
            DispatchTaskCreateRequest taskRequest = new DispatchTaskCreateRequest();
            taskRequest.setOrderId(order.getOrderId());
            taskRequest.setDispatchType("AUTO");
            taskRequest.setRemark("scale-load");
            DispatchTaskCreateResponse task = dispatchTaskService.createTask(taskRequest);
            DispatchTaskAssignResponse assign = dispatchTaskService.autoAssignTask(task.getTaskId());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

            if ("ASSIGNED".equals(assign.getStatus()) && assign.getVehicleId() != null) {
                assigned++;
                latencies.add(elapsedMs);
                completeTask(assign, order.getOrderId());
            } else {
                String dbMessage = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(fail_reason_msg, '') FROM t_dispatch_task WHERE id = ?",
                        String.class, task.getTaskId());
                failureReasons.add(assign.getStatus() + ":" + assign.getFailReasonCode()
                        + "|" + assign.getReasonCode() + "|" + assign.getReasonMessage()
                        + "|db=" + dbMessage);
            }
        }

        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        double successRate = assigned / (double) ORDERS;
        RedisProbe probe = probeRedisRoundTrip(500);
        writeReport(assigned, failureReasons, p50, p95, p99, successRate, probe);

        assertTrue(successRate >= MIN_SUCCESS_RATE,
                "完成率 " + String.format("%.2f%%", successRate * 100) + " 低于下限 "
                        + String.format("%.0f%%", MIN_SUCCESS_RATE * 100)
                        + "；失败原因分布=" + failureReasons.stream().distinct().limit(3).toList());
        assertTrue(p95 <= P95_BUDGET_MS, "派单 P95=" + p95 + "ms 超过预算 " + P95_BUDGET_MS + "ms");
        assertEquals(ORDERS, assigned + failureReasons.size(), "每笔订单都必须有明确结果");
    }

    private void completeTask(DispatchTaskAssignResponse assign, Long orderId) {
        VehicleEntity vehicle = vehicleMapper.selectById(assign.getVehicleId());
        vehicleReportService.handleReport(report(vehicle.getVehicleCode(), assign.getTaskId(), orderId, "START_EXECUTE"));
        vehicleReportService.handleReport(report(vehicle.getVehicleCode(), assign.getTaskId(), orderId, "TASK_SUCCESS"));
    }

    private VehicleReportRequest report(String vehicleCode, Long taskId, Long orderId, String reportType) {
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

    private OrderCreateRequest orderRequest(int index) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setExternalOrderNo("SCALE-" + index);
        request.setSourceType("MANUAL");
        request.setBizType("DELIVERY");
        request.setPickupPointId(PICKUP_STATION_ID);
        request.setDropoffPointId(DROPOFF_STATION_ID);
        request.setPriority("P1");
        request.setRemark("scale-load");
        return request;
    }

    /** 插入 GRID_COLUMNS × GRID_ROWS 的网格路网（默认 40 × 25 = 1000 节点）。 */
    private void seedRoadNetwork() {
        List<Object[]> nodes = new ArrayList<>();
        for (int column = 0; column < GRID_COLUMNS; column++) {
            for (int row = 0; row < GRID_ROWS; row++) {
                nodes.add(new Object[]{nodeCode(column, row),
                        (double) (GRID_ORIGIN + column * GRID_SPACING),
                        (double) (GRID_ORIGIN + row * GRID_SPACING)});
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_road_node (park_id, node_code, coord_x, coord_y, status, version, deleted)
                VALUES (1, ?, ?, ?, 'ACTIVE', 0, 0)
                """, nodes);

        List<Object[]> segments = new ArrayList<>();
        for (int column = 0; column < GRID_COLUMNS; column++) {
            for (int row = 0; row < GRID_ROWS; row++) {
                if (column + 1 < GRID_COLUMNS) {
                    segments.add(link(nodeCode(column, row), nodeCode(column + 1, row)));
                }
                if (row + 1 < GRID_ROWS) {
                    segments.add(link(nodeCode(column, row), nodeCode(column, row + 1)));
                }
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_road_segment (park_id, from_node_code, to_node_code, direction, status,
                                            speed_limit_kmh, congestion_level, version, deleted)
                VALUES (?, ?, ?, 'BIDIRECTIONAL', 'ACTIVE', 15, 0, 0, 0)
                """, segments);

        assertEquals(EFFECTIVE_NODES, nodes.size(),
                "网格规模必须与压测声明一致（目标 " + NETWORK_NODES + "，实际 " + nodes.size() + " 节点）");
    }

    private static Object[] link(String from, String to) {
        return new Object[]{1L, from, to};
    }

    private static String nodeCode(int column, int row) {
        return "G" + column + "_" + row;
    }

    /**
     * 车辆沿路网分散停放：位置写入 current_longitude/current_latitude（schematic x/y）。
     *
     * <p>车号必须用 {@link com.fsd.dispatch.fleet.PilotFleetSupport#GEO_VEHICLE_PREFIX}（ZJF-AV-）前缀：
     * 示意池已随 §7.6 删除，其他前缀会被 {@code matchesOrderFleet} 整体过滤掉。
     */
    private void seedVehicles() {
        List<Object[]> vehicles = new ArrayList<>();
        for (int index = 0; index < VEHICLES; index++) {
            int column = index % GRID_COLUMNS;
            int row = (index * 7) % GRID_ROWS;
            vehicles.add(new Object[]{
                    "ZJF-AV-" + String.format("%03d", index + 1),
                    "Scale Vehicle " + (index + 1),
                    (double) (GRID_ORIGIN + column * GRID_SPACING),
                    (double) (GRID_ORIGIN + row * GRID_SPACING)});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_vehicle (park_id, vehicle_code, vehicle_name, vehicle_type, link_mode,
                                       online_status, dispatch_status, current_longitude, current_latitude,
                                       battery_level, last_report_time, version, deleted)
                VALUES (1, ?, ?, 'CAR', 'SIM', 'ONLINE', 'IDLE', ?, ?, 100, CURRENT_TIMESTAMP, 0, 0)
                """, vehicles);
    }

    private static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile / 100.0D) - 1);
        return sorted.get(index);
    }

    private void writeReport(int assigned, List<String> failureReasons,
                             long p50, long p95, long p99, double successRate,
                             RedisProbe probe) throws IOException {
        Path directory = Path.of("target", "scale-report");
        Files.createDirectories(directory);

        String redisSection;
        if (probe.p50Micros() < 0L) {
            redisSection = "- **Redis 模式：FAKE（内存假实现）** —— 端点 " + probe.endpoint()
                    + " 不可达，MAPF 时空预约走带时间桶过期的内存实现；\n"
                    + "  **本组延迟不含 Redis 网络往返**，不可直接当作生产数字引用。\n"
                    + "  如需含往返的口径：先启动 Redis（`redis-server --port 6379`）再重跑本测试。\n";
        } else {
            redisSection = "- **Redis 模式：REAL（真实 Redis）** —— 端点 " + probe.endpoint()
                    + "，MAPF 时空预约走真实 SET/GET；\n"
                    + "  **本组延迟已包含 Redis 网络往返**。\n"
                    + String.format("- Redis 往返探针（%d 次 SET+GET）：P50 %d µs / P95 %d µs%n",
                            probe.rounds(), probe.p50Micros(), probe.p95Micros());
        }

        String content = "# 派单规模承载压测报告\n\n"
                + "- 车辆数：" + VEHICLES + "\n"
                + "- 订单数：" + ORDERS + "\n"
                + "- 路网节点数：" + EFFECTIVE_NODES + "（" + GRID_COLUMNS + " × " + GRID_ROWS + " 网格，双向边）\n"
                + "- 派单成功：" + assigned + "（" + String.format("%.2f%%", successRate * 100) + "）\n"
                + "- 失败：" + failureReasons.size() + "\n"
                + redisSection
                + "\n| 分位 | 端到端派单延迟（下单 → 建任务 → 选车） |\n"
                + "| --- | --- |\n"
                + "| P50 | " + p50 + " ms |\n"
                + "| P95 | " + p95 + " ms |\n"
                + "| P99 | " + p99 + " ms |\n"
                + "\n失败原因分布：" + (failureReasons.isEmpty()
                        ? "无" : failureReasons.stream().distinct().toList()) + "\n";
        // T-04：按 Redis 口径分别留档（REAL / FAKE），两套延迟定义不同，绝不写同一个文件名，
        // 避免不同口径互相覆盖后被误当作同一件事引用。
        Files.writeString(directory.resolve("dispatch-scale-report-" + probe.mode().toLowerCase() + ".md"),
                content, StandardCharsets.UTF_8);
    }

    /**
     * Redis 往返回合探针（T-04）：REAL 模式下执行 {@code rounds} 次 SET+GET，
     * 用于把"网络往返回合"这一项从派单 P95 里单独量化出来。
     *
     * @return FAKE 模式下 {@code p50Micros = -1}，表示未测量
     */
    private RedisProbe probeRedisRoundTrip(int rounds) {
        String mode = redisMode();
        String endpoint = redisEndpoint();
        if (!"REAL".equals(mode)) {
            return new RedisProbe(mode, endpoint, 0, -1L, -1L);
        }
        List<Long> roundTrips = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < rounds; index++) {
            String key = "scale:probe:" + index;
            keys.add(key);
            long started = System.nanoTime();
            redisTemplate.opsForValue().set(key, "v" + index);
            redisTemplate.opsForValue().get(key);
            roundTrips.add((System.nanoTime() - started) / 1_000L);
        }
        redisTemplate.delete(keys);
        return new RedisProbe(mode, endpoint, rounds,
                percentile(roundTrips, 50), percentile(roundTrips, 95));
    }

    /** 当前生效的 Redis 实现：REAL（真实）/ FAKE（内存假实现）。 */
    private String redisMode() {
        return Mockito.mockingDetails(redisTemplate).isMock() ? "FAKE" : "REAL";
    }

    private static String redisEndpoint() {
        return redisHost() + ":" + redisPort();
    }

    private static String redisHost() {
        return System.getProperty("spring.data.redis.host",
                System.getenv().getOrDefault("REDIS_HOST", "localhost"));
    }

    private static int redisPort() {
        String raw = System.getProperty("spring.data.redis.port",
                System.getenv().getOrDefault("REDIS_PORT", "6379"));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 6379;
        }
    }

    /** Redis 是否可达（T-04 模式判定依据）。 */
    static boolean isRedisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost(), redisPort()), 500);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /** 一次 Redis 往返回合探针的结果；{@code p50Micros < 0} 表示未测量（FAKE 模式）。 */
    private record RedisProbe(String mode, String endpoint, int rounds,
                              long p50Micros, long p95Micros) {
    }

    /**
     * 测试用假 Redis（仅在真实 Redis 不可达时生效，见 {@link RedisUnavailableCondition}）：
     * 只实现 MAPF 预约表用到的 {@code opsForValue().get / setIfAbsent / delete}，
     * 并按 key 末尾的时间桶做惰性过期，避免长链压测下内存无限增长。
     *
     * <p>生产侧 MapfReservationService 的语义（先查对向 key、再 setIfAbsent 正向 key）在假实现里
     * 完整保留，因此冲突检测与重规划行为与 Redis 版本一致，仅去掉网络 I/O。
     */
    @TestConfiguration
    static class FakeRedisConfig {

        /** 与 MapfProperties.bucketMs 默认值一致；仅用于判断 key 是否过期。 */
        private static final long BUCKET_MS = 500L;
        /** 过期冗余桶数：超过当前桶 + 该值即视为失效。 */
        private static final long STALE_BUCKET_MARGIN = 16L;

        @Bean
        @Primary
        @Conditional(RedisUnavailableCondition.class)
        StringRedisTemplate fakeStringRedisTemplate() {
            Map<String, String> store = new ConcurrentHashMap<>();
            ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);

            Mockito.when(valueOps.get(Mockito.anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return isStale(key) ? null : store.get(key);
            });
            Mockito.when(valueOps.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class)))
                    .thenAnswer(invocation -> {
                        String key = invocation.getArgument(0);
                        String value = invocation.getArgument(1);
                        if (isStale(key)) {
                            return Boolean.TRUE;
                        }
                        return store.putIfAbsent(key, value) == null;
                    });

            StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
            Mockito.when(template.opsForValue()).thenReturn(valueOps);
            Mockito.when(template.delete(Mockito.anyCollection())).thenAnswer(invocation -> {
                Collection<String> keys = invocation.getArgument(0);
                long removed = keys.stream().filter(key -> store.remove(key) != null).count();
                return removed;
            });
            return template;
        }

        private static boolean isStale(String key) {
            int separator = key.lastIndexOf(':');
            if (separator < 0 || separator == key.length() - 1) {
                return false;
            }
            try {
                long bucket = Long.parseLong(key.substring(separator + 1));
                long currentBucket = System.currentTimeMillis() / BUCKET_MS;
                return bucket < currentBucket - STALE_BUCKET_MARGIN;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    /**
     * 真实 Redis 不可达时成立（T-04）：仅在此时注册 {@link FakeRedisConfig} 的内存假实现。
     *
     * <p>可达时条件为 false、假 bean 不注册，注入到测试的即为应用真实的
     * {@code StringRedisTemplate}，MAPF 时空预约因此真正走 Redis 网络往返。
     */
    static class RedisUnavailableCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isRedisReachable();
        }
    }
}
