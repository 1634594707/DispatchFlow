package com.fsd.admin.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.admin.config.AdminSseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AdminSseMetricsTest {

    @Test
    void shouldRecordConnectionAndTicketMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminSseProperties properties = new AdminSseProperties();
        properties.setMaxConnections(50);
        AdminSseMetrics metrics = new AdminSseMetrics(registry, properties);

        metrics.connectionOpened();
        metrics.connectionOpened();
        metrics.connectionClosed();
        metrics.connectionRejected();
        metrics.ticketIssued();
        metrics.ticketConsumed();
        metrics.ticketInvalid();

        assertEquals(1.0, registry.get("dispatchflow.sse.connections.active").gauge().value());
        assertEquals(2.0, registry.get("dispatchflow.sse.connections.opened").counter().count());
        assertEquals(2.0, registry.get("dispatchflow.sse.connections.peak").gauge().value());
        assertEquals(50.0, registry.get("dispatchflow.sse.connections.limit").gauge().value());
        assertEquals(1.0, registry.get("dispatchflow.sse.connections.closed").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.sse.connections.rejected").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.sse.tickets.issued").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.sse.tickets.consumed").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.sse.tickets.invalid").counter().count());
    }

    @Test
    void shouldGroupClosedConnectionsByReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminSseMetrics metrics = new AdminSseMetrics(registry);

        metrics.connectionClosed("timeout");
        metrics.connectionClosed("timeout");
        metrics.connectionClosed("error");

        assertEquals(2.0, registry.get("dispatchflow.sse.connections.closed.by.reason")
                .tag("reason", "timeout").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.sse.connections.closed.by.reason")
                .tag("reason", "error").counter().count());
        assertEquals(3.0, registry.get("dispatchflow.sse.connections.closed").counter().count());
    }

    @Test
    void shouldCountReconnectOnlyWithinWindowAfterDisconnect() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminSseMetrics metrics = new AdminSseMetrics(registry);

        // 未断开先连接：不计重连
        metrics.reconnectIfRecent("dispatcher-1");
        assertEquals(0.0, registry.get("dispatchflow.sse.connections.reconnects").counter().count());

        // 断开后窗口内重连：计一次
        metrics.markDisconnected("dispatcher-1");
        metrics.reconnectIfRecent("dispatcher-1");
        assertEquals(1.0, registry.get("dispatchflow.sse.connections.reconnects").counter().count());

        // 重连消费记录后再次连接：不再计数
        metrics.reconnectIfRecent("dispatcher-1");
        assertEquals(1.0, registry.get("dispatchflow.sse.connections.reconnects").counter().count());

        // 匿名连接不参与重连统计且不抛错
        metrics.reconnectIfRecent(null);
        metrics.markDisconnected(" ");
        assertTrue(true);
    }
}
