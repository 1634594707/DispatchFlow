package com.fsd.admin.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基础设施 Prometheus 指标绑定（路线图 Phase 5）。
 *
 * <ul>
 *   <li>RabbitMQ：各业务队列积压深度 gauge（dispatchflow.rabbitmq.queue.backlog）；</li>
 *   <li>Redis：可用性（0/1）与最近一次 ping 延迟；</li>
 * </ul>
 */
@Component
public class InfraMetricsBinder {

    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final java.util.List<Queue> streamQueues;
    private final AtomicInteger redisAvailable = new AtomicInteger(0);
    private final AtomicLong redisPingLatencyMs = new AtomicLong(-1);

    public InfraMetricsBinder(ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                              ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                              java.util.List<Queue> queues,
                              MeterRegistry registry) {
        this.rabbitAdminProvider = rabbitAdminProvider;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.streamQueues = queues;

        for (Queue queue : queues) {
            String name = queue.getName();
            Gauge.builder("dispatchflow.rabbitmq.queue.backlog", this, binder -> binder.queueDepth(name))
                    .description("Business queue message backlog")
                    .tag("queue", name)
                    .register(registry);
        }
        Gauge.builder("dispatchflow.redis.available", redisAvailable, AtomicInteger::get)
                .description("Redis availability from periodic ping (1=available)")
                .register(registry);
        Gauge.builder("dispatchflow.redis.ping.latency.ms", redisPingLatencyMs, AtomicLong::get)
                .description("Last Redis ping latency in milliseconds (-1 = not measured yet)")
                .register(registry);
    }

    /** 周期性 Redis ping，刷新可用性与延迟指标。 */
    @Scheduled(fixedDelay = 30000L)
    public void probeRedis() {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            redisAvailable.set(0);
            return;
        }
        long start = System.currentTimeMillis();
        try (var connection = factory.getConnection()) {
            connection.ping();
            redisPingLatencyMs.set(System.currentTimeMillis() - start);
            redisAvailable.set(1);
        } catch (Exception ex) {
            redisAvailable.set(0);
        }
    }

    private double queueDepth(String queueName) {
        RabbitAdmin admin = rabbitAdminProvider.getIfAvailable();
        if (admin == null) {
            return 0;
        }
        var properties = admin.getQueueProperties(queueName);
        if (properties == null || !properties.containsKey(RabbitAdmin.QUEUE_MESSAGE_COUNT)) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)));
    }
}