package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.metrics.ApiRequestMetrics;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.admin.service.SystemHealthAdminService;
import com.fsd.admin.vo.AdminDetailedMetricsResponse;
import com.fsd.admin.vo.AdminSystemComponentHealth;
import com.fsd.admin.vo.AdminSystemHealthResponse;
import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
import com.fsd.dispatch.metrics.DispatchLockMetrics;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.stereotype.Service;
import com.zaxxer.hikari.HikariDataSource;

@Service
public class SystemHealthAdminServiceImpl implements SystemHealthAdminService {

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final DispatchEventOutboxMapper outboxMapper;
    private final ApiRequestMetrics apiRequestMetrics;
    private final AdminDispatchStreamService dispatchStreamService;
    private final DispatchLockMetrics dispatchLockMetrics;

    public SystemHealthAdminServiceImpl(DataSource dataSource,
                                        ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                                        ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider,
                                        ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                                        DispatchEventOutboxMapper outboxMapper,
                                        ApiRequestMetrics apiRequestMetrics,
                                        AdminDispatchStreamService dispatchStreamService,
                                        DispatchLockMetrics dispatchLockMetrics) {
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.rabbitConnectionFactoryProvider = rabbitConnectionFactoryProvider;
        this.rabbitAdminProvider = rabbitAdminProvider;
        this.outboxMapper = outboxMapper;
        this.apiRequestMetrics = apiRequestMetrics;
        this.dispatchStreamService = dispatchStreamService;
        this.dispatchLockMetrics = dispatchLockMetrics;
    }

    @Override
    public AdminSystemHealthResponse getHealth() {
        List<AdminSystemComponentHealth> components = new ArrayList<>();
        components.add(checkDatabase());
        components.add(checkRedis());
        components.add(checkRabbitMq());
        components.add(checkOutbox());
        components.add(checkApiMetrics());
        components.add(checkJvmResources());
        String overall = components.stream().anyMatch(item -> "DOWN".equals(item.getStatus()))
                ? "DOWN"
                : components.stream().anyMatch(item -> "DEGRADED".equals(item.getStatus()))
                ? "DEGRADED"
                : "UP";
        return AdminSystemHealthResponse.builder()
                .overallStatus(overall)
                .checkedAt(LocalDateTime.now())
                .components(components)
                .build();
    }

    @Override
    public AdminDetailedMetricsResponse getDetailedMetrics() {
        return AdminDetailedMetricsResponse.builder()
                .mqBacklogs(getMqBacklogs())
                .dbConnectionPool(getDbConnectionPool())
                .redisMemory(getRedisMemory())
                .sseConnections(AdminDetailedMetricsResponse.SseConnection.builder()
                        .activeConnections(dispatchStreamService.getActiveConnectionCount())
                        .status("OK")
                        .build())
                .dispatchLock(getDispatchLockMetric())
                .apiP99Latency(getApiLatency())
                .build();
    }

    private List<AdminDetailedMetricsResponse.MqQueueBacklog> getMqBacklogs() {
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        if (rabbitAdmin == null) {
            return List.of(AdminDetailedMetricsResponse.MqQueueBacklog.builder()
                    .queueName("RabbitMQ 未接入后端")
                    .backlog(0)
                    .status("WARNING")
                    .build());
        }
        return List.of(
                queueBacklog(rabbitAdmin, DispatchMessagingConfig.DISPATCH_AUDIT_QUEUE),
                queueBacklog(rabbitAdmin, DispatchMessagingConfig.DISPATCH_STREAM_QUEUE),
                queueBacklog(rabbitAdmin, DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE));
    }

    private AdminDetailedMetricsResponse.MqQueueBacklog queueBacklog(RabbitAdmin rabbitAdmin, String queueName) {
        int backlog = queueDepth(rabbitAdmin, queueName);
        return AdminDetailedMetricsResponse.MqQueueBacklog.builder()
                .queueName(queueName)
                .backlog(backlog)
                .status(backlog > 500 ? "CRITICAL" : backlog > 100 ? "WARNING" : "OK")
                .build();
    }

    private AdminDetailedMetricsResponse.DbConnectionPool getDbConnectionPool() {
        if (dataSource instanceof HikariDataSource hikariDataSource && hikariDataSource.getHikariPoolMXBean() != null) {
            var pool = hikariDataSource.getHikariPoolMXBean();
            int active = pool.getActiveConnections();
            int idle = pool.getIdleConnections();
            int max = hikariDataSource.getMaximumPoolSize();
            double usagePercent = max <= 0 ? 0D : active * 100D / max;
            return AdminDetailedMetricsResponse.DbConnectionPool.builder()
                    .active(active)
                    .idle(idle)
                    .max(max)
                    .usagePercent(usagePercent)
                    .status(usagePercent > 90 ? "CRITICAL" : usagePercent > 75 ? "WARNING" : "OK")
                    .build();
        }
        return AdminDetailedMetricsResponse.DbConnectionPool.builder()
                .active(0)
                .idle(0)
                .max(0)
                .usagePercent(0D)
                .status("WARNING")
                .build();
    }

    private AdminDetailedMetricsResponse.RedisMemory getRedisMemory() {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return redisMemoryNotAvailable();
        }
        try (var connection = factory.getConnection()) {
            RedisServerCommands serverCommands = connection.serverCommands();
            Properties memory = serverCommands.info("memory");
            if (memory == null) {
                return redisMemoryNotAvailable();
            }
            long used = Long.parseLong(memory.getProperty("used_memory", "0"));
            long max = Long.parseLong(memory.getProperty("maxmemory", "0"));
            double usagePercent = max <= 0 ? 0D : used * 100D / max;
            return AdminDetailedMetricsResponse.RedisMemory.builder()
                    .usedBytes(used)
                    .maxBytes(max)
                    .usagePercent(usagePercent)
                    .status(max <= 0 ? "WARNING" : usagePercent > 90 ? "CRITICAL" : usagePercent > 75 ? "WARNING" : "OK")
                    .build();
        } catch (RuntimeException ex) {
            return redisMemoryNotAvailable();
        }
    }

    private AdminDetailedMetricsResponse.RedisMemory redisMemoryNotAvailable() {
        return AdminDetailedMetricsResponse.RedisMemory.builder()
                .usedBytes(0L)
                .maxBytes(0L)
                .usagePercent(0D)
                .status("WARNING")
                .build();
    }

    private AdminDetailedMetricsResponse.DispatchLockMetric getDispatchLockMetric() {
        long maxHeldMs = dispatchLockMetrics.getMaxHeldDurationMs();
        return AdminDetailedMetricsResponse.DispatchLockMetric.builder()
                .acquireFailureCount(dispatchLockMetrics.getAcquireFailureCount())
                .heldDurationCount(dispatchLockMetrics.getHeldDurationCount())
                .averageHeldDurationMs(dispatchLockMetrics.getAverageHeldDurationMs())
                .maxHeldDurationMs(maxHeldMs)
                .status(maxHeldMs > 8000 ? "WARNING" : "OK")
                .build();
    }

    private AdminDetailedMetricsResponse.ApiP99Latency getApiLatency() {
        long p50 = apiRequestMetrics.getRecentPercentileDurationMs(0.50D);
        long p95 = apiRequestMetrics.getRecentPercentileDurationMs(0.95D);
        long p99 = apiRequestMetrics.getRecentPercentileDurationMs(0.99D);
        return AdminDetailedMetricsResponse.ApiP99Latency.builder()
                .currentMs(apiRequestMetrics.getRecentAverageDurationMs())
                .p50Ms(p50)
                .p95Ms(p95)
                .p99Ms(p99)
                .status(p99 > 2000 ? "CRITICAL" : p99 > 1000 ? "WARNING" : "OK")
                .history(List.of(AdminDetailedMetricsResponse.ApiLatencyHistoryPoint.builder()
                        .time(LocalDateTime.now().toString())
                        .value(p99)
                        .build()))
                .build();
    }

    private AdminSystemComponentHealth checkDatabase() {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1")) {
            resultSet.next();
            return component("mysql", "UP", "数据库连接正常", Map.of("catalog", connection.getCatalog()));
        } catch (Exception ex) {
            return component("mysql", "DOWN", "数据库不可用: " + ex.getMessage(), Map.of());
        }
    }

    private AdminSystemComponentHealth checkRedis() {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return component("redis", "DEGRADED", "Redis 未配置", Map.of());
        }
        try (var connection = factory.getConnection()) {
            String pong = connection.ping();
            return component("redis", "UP", "Redis 连接正常", Map.of("ping", pong));
        } catch (Exception ex) {
            return component("redis", "DOWN", "Redis 不可用: " + ex.getMessage(), Map.of());
        }
    }

    private AdminSystemComponentHealth checkRabbitMq() {
        ConnectionFactory factory = rabbitConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return component("rabbitmq", "DEGRADED", "RabbitMQ 未配置", Map.of());
        }
        try (var connection = factory.createConnection()) {
            Map<String, Object> queueDepths = new LinkedHashMap<>();
            RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
            if (rabbitAdmin != null) {
                queueDepths.put(DispatchMessagingConfig.DISPATCH_AUDIT_QUEUE, queueDepth(rabbitAdmin, DispatchMessagingConfig.DISPATCH_AUDIT_QUEUE));
                queueDepths.put(DispatchMessagingConfig.DISPATCH_STREAM_QUEUE, queueDepth(rabbitAdmin, DispatchMessagingConfig.DISPATCH_STREAM_QUEUE));
                queueDepths.put(DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE, queueDepth(rabbitAdmin, DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE));
            }
            long backlog = queueDepths.values().stream()
                    .filter(Number.class::isInstance)
                    .mapToLong(value -> ((Number) value).longValue())
                    .sum();
            String status = backlog > 500 ? "DEGRADED" : "UP";
            String message = backlog > 500 ? "消息队列积压偏高" : "RabbitMQ 连接正常";
            Map<String, Object> details = new LinkedHashMap<>(queueDepths);
            details.put("totalBacklog", backlog);
            return component("rabbitmq", status, message, details);
        } catch (Exception ex) {
            return component("rabbitmq", "DOWN", "RabbitMQ 不可用: " + ex.getMessage(), Map.of());
        }
    }

    private int queueDepth(RabbitAdmin rabbitAdmin, String queueName) {
        Properties properties = rabbitAdmin.getQueueProperties(queueName);
        if (properties == null || properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT) == null) {
            return 0;
        }
        return Integer.parseInt(properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT).toString());
    }

    private AdminSystemComponentHealth checkOutbox() {
        long pending = outboxMapper.selectCount(new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .eq(DispatchEventOutboxEntity::getStatus, "PENDING"));
        long failed = outboxMapper.selectCount(new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .eq(DispatchEventOutboxEntity::getStatus, "FAILED"));
        String status = failed > 20 || pending > 100 ? "DEGRADED" : "UP";
        return component("event-outbox", status, "事件 Outbox 状态",
                Map.of("pending", pending, "failed", failed));
    }

    private AdminSystemComponentHealth checkApiMetrics() {
        long avg = apiRequestMetrics.getRecentAverageDurationMs();
        long max = apiRequestMetrics.getMaxDurationMs();
        String status = avg > 2000 ? "DEGRADED" : "UP";
        return component("api-latency", status, "API 响应时间统计",
                Map.of(
                        "requestCount", apiRequestMetrics.getRequestCount(),
                        "averageMs", avg,
                        "maxMs", max,
                        "recentAverageMs", apiRequestMetrics.getRecentAverageDurationMs()));
    }

    private AdminSystemComponentHealth checkJvmResources() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        long usedHeap = memory.getHeapMemoryUsage().getUsed();
        long maxHeap = memory.getHeapMemoryUsage().getMax();
        double heapUsage = maxHeap <= 0 ? 0 : (usedHeap * 100.0 / maxHeap);
        String status = heapUsage > 90 ? "DEGRADED" : "UP";
        return component("jvm", status, "JVM 资源使用率",
                Map.of(
                        "heapUsagePercent", Math.round(heapUsage),
                        "processors", os.getAvailableProcessors(),
                        "systemLoadAverage", os.getSystemLoadAverage()));
    }

    private AdminSystemComponentHealth component(String name, String status, String message, Map<String, Object> details) {
        return AdminSystemComponentHealth.builder()
                .name(name)
                .status(status)
                .message(message)
                .details(details)
                .build();
    }
}
