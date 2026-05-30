package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.metrics.ApiRequestMetrics;
import com.fsd.admin.service.SystemHealthAdminService;
import com.fsd.admin.vo.AdminSystemComponentHealth;
import com.fsd.admin.vo.AdminSystemHealthResponse;
import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
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
import org.springframework.stereotype.Service;

@Service
public class SystemHealthAdminServiceImpl implements SystemHealthAdminService {

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final DispatchEventOutboxMapper outboxMapper;
    private final ApiRequestMetrics apiRequestMetrics;

    public SystemHealthAdminServiceImpl(DataSource dataSource,
                                        ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                                        ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider,
                                        ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                                        DispatchEventOutboxMapper outboxMapper,
                                        ApiRequestMetrics apiRequestMetrics) {
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.rabbitConnectionFactoryProvider = rabbitConnectionFactoryProvider;
        this.rabbitAdminProvider = rabbitAdminProvider;
        this.outboxMapper = outboxMapper;
        this.apiRequestMetrics = apiRequestMetrics;
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
