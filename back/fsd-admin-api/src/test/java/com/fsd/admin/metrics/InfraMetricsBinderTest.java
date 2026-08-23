package com.fsd.admin.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 路线图 Phase 5：RabbitMQ 队列积压与 Redis 可用性 Prometheus 指标。
 */
class InfraMetricsBinderTest {

    private RabbitAdmin rabbitAdmin;
    private ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private RedisConnectionFactory redisConnectionFactory;
    private ObjectProvider<RedisConnectionFactory> redisProvider;
    private SimpleMeterRegistry registry;
    private Queue streamQueue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rabbitAdmin = mock(RabbitAdmin.class);
        rabbitAdminProvider = mock(ObjectProvider.class);
        when(rabbitAdminProvider.getIfAvailable()).thenReturn(rabbitAdmin);
        redisConnectionFactory = mock(RedisConnectionFactory.class);
        redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisConnectionFactory);
        registry = new SimpleMeterRegistry();
        streamQueue = new AnonymousQueue();
    }

    private InfraMetricsBinder binder() {
        return new InfraMetricsBinder(
                rabbitAdminProvider,
                redisProvider,
                List.of(new Queue("fsd.dispatch.audit.queue", true), streamQueue),
                registry);
    }

    @Test
    void shouldRegisterQueueBacklogGaugesForAllBusinessQueues() {
        when(rabbitAdmin.getQueueProperties("fsd.dispatch.audit.queue")).thenReturn(propsWithCount(7));
        when(rabbitAdmin.getQueueProperties(streamQueue.getName())).thenReturn(propsWithCount(0));

        InfraMetricsBinder binder = binder();

        assertEquals(7.0, registry.get("dispatchflow.rabbitmq.queue.backlog")
                .tag("queue", "fsd.dispatch.audit.queue").gauge().value());
        assertEquals(0.0, registry.get("dispatchflow.rabbitmq.queue.backlog")
                .tag("queue", streamQueue.getName()).gauge().value());
    }

    @Test
    void redisOutageShouldSetAvailableZero() {
        InfraMetricsBinder binder = binder();

        when(redisConnectionFactory.getConnection())
                .thenThrow(new RedisConnectionFailureException("down"));
        binder.probeRedis();

        assertEquals(0.0, registry.get("dispatchflow.redis.available").gauge().value());
    }

    @Test
    void redisRecoveryShouldSetAvailableOne() {
        InfraMetricsBinder binder = binder();

        RedisConnection connection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(connection);
        binder.probeRedis();

        assertEquals(1.0, registry.get("dispatchflow.redis.available").gauge().value());
    }

    @Test
    void missingRabbitAdminShouldReportZeroBacklog() {
        when(rabbitAdminProvider.getIfAvailable()).thenReturn(null);
        when(rabbitAdmin.getQueueProperties(anyString())).thenReturn(propsWithCount(9));
        InfraMetricsBinder binder = binder();
        assertEquals(0.0, registry.get("dispatchflow.rabbitmq.queue.backlog")
                .tag("queue", "fsd.dispatch.audit.queue").gauge().value());
    }

    private Properties propsWithCount(int count) {
        Properties properties = new Properties();
        // QUEUE_MESSAGE_COUNT 常量为 Object 类型，Properties 走 put
        properties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, count);
        return properties;
    }
}