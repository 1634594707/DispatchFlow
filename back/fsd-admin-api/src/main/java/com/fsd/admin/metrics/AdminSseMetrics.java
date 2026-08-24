package com.fsd.admin.metrics;

import com.fsd.admin.config.AdminSseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * SSE 实时连接与 ticket 指标（路线图 4.1）。
 *
 * <p>覆盖：活跃/峰值/配置上限连接数、建立成功、拒绝（含超限）、断开原因分类、
 * 重连次数，以及 ticket 签发/消费/失效。</p>
 */
@Component
public class AdminSseMetrics {

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger peakConnections = new AtomicInteger();
    private final Counter opened;
    private final Counter closed;
    private final Counter rejected;
    private final Counter reconnects;
    private final Counter ticketsIssued;
    private final Counter ticketsConsumed;
    private final Counter ticketsInvalid;
    /** 断开原因 -> 计数器（completed / timeout / error / send-failure）。 */
    private final Map<String, Counter> closedByReason = new ConcurrentHashMap<>();
    /** 用户名 -> 最近断开时间戳，用于重连识别。 */
    private final Map<String, Long> recentDisconnections = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    private static final long RECONNECT_WINDOW_MS = 60_000L;

    /** 主构造器：Spring 注入入口（存在测试用便利构造器，须显式标注主选）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AdminSseMetrics(MeterRegistry registry, AdminSseProperties properties) {
        this.registry = registry;
        Gauge.builder("dispatchflow.sse.connections.active", activeConnections, AtomicInteger::get)
                .description("Active admin SSE connections")
                .register(registry);
        Gauge.builder("dispatchflow.sse.connections.peak", peakConnections, AtomicInteger::get)
                .description("Peak admin SSE connections since startup")
                .register(registry);
        Gauge.builder("dispatchflow.sse.connections.limit", properties.getMaxConnections(), value -> value)
                .description("Configured admin SSE connection limit")
                .register(registry);
        opened = Counter.builder("dispatchflow.sse.connections.opened")
                .description("Admin SSE connections opened")
                .register(registry);
        closed = Counter.builder("dispatchflow.sse.connections.closed")
                .description("Admin SSE connections closed (all reasons)")
                .register(registry);
        rejected = Counter.builder("dispatchflow.sse.connections.rejected")
                .description("Admin SSE connection attempts rejected")
                .register(registry);
        reconnects = Counter.builder("dispatchflow.sse.connections.reconnects")
                .description("Reconnects observed within the reconnect window")
                .register(registry);
        ticketsIssued = Counter.builder("dispatchflow.sse.tickets.issued")
                .description("SSE tickets issued")
                .register(registry);
        ticketsConsumed = Counter.builder("dispatchflow.sse.tickets.consumed")
                .description("SSE tickets consumed")
                .register(registry);
        ticketsInvalid = Counter.builder("dispatchflow.sse.tickets.invalid")
                .description("Invalid or expired SSE tickets")
                .register(registry);
    }

    /** 便捷构造：使用默认 SSE 配置（测试用）。 */
    public AdminSseMetrics(MeterRegistry registry) {
        this(registry, new AdminSseProperties());
    }

    public void connectionOpened() {
        int current = activeConnections.incrementAndGet();
        peakConnections.updateAndGet(peak -> Math.max(peak, current));
        opened.increment();
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        closed.increment();
    }

    /** 按原因记录断开：completed（正常完成）/ timeout / error / send-failure。 */
    public void connectionClosed(String reason) {
        connectionClosed();
        closedByReason.computeIfAbsent(reason == null || reason.isBlank() ? "unknown" : reason,
                key -> Counter.builder("dispatchflow.sse.connections.closed.by.reason")
                        .description("Admin SSE connections closed grouped by reason")
                        .tag("reason", key)
                        .register(registry))
                .increment();
    }

    public void connectionRejected() {
        rejected.increment();
    }

    public void reconnectObserved() {
        reconnects.increment();
    }

    /** 断开时记录用户与时间，供重连识别。 */
    public void markDisconnected(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        recentDisconnections.put(username, System.currentTimeMillis());
        if (recentDisconnections.size() > 1024) {
            recentDisconnections.entrySet()
                    .removeIf(entry -> System.currentTimeMillis() - entry.getValue() > RECONNECT_WINDOW_MS);
        }
    }

    /** 同一用户在窗口期内断开后重连，计一次重连。 */
    public void reconnectIfRecent(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Long lastDisconnectedAt = recentDisconnections.remove(username);
        if (lastDisconnectedAt != null
                && System.currentTimeMillis() - lastDisconnectedAt <= RECONNECT_WINDOW_MS) {
            reconnects.increment();
        }
    }

    public void ticketIssued() {
        ticketsIssued.increment();
    }

    public void ticketConsumed() {
        ticketsConsumed.increment();
    }

    public void ticketInvalid() {
        ticketsInvalid.increment();
    }
}
