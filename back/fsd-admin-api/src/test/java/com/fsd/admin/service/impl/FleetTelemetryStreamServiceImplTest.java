package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fsd.admin.config.AdminSseProperties;
import com.fsd.common.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 路线图 4.1：车辆监控流与调度流统一认证、超时、上限与心跳策略。
 */
class FleetTelemetryStreamServiceImplTest {

    private AdminSseProperties properties;
    private SimpleMeterRegistry registry;
    private FleetTelemetryStreamServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AdminSseProperties();
        registry = new SimpleMeterRegistry();
        service = new FleetTelemetryStreamServiceImpl(properties, registry);
    }

    /** CapturingEmitter：记录 send 的帧，可模拟断连。 */
    private static class CapturingEmitter extends SseEmitter {
        int sendCount = 0;
        boolean broken = false;

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("broken pipe");
            }
            sendCount++;
        }
    }

    @Test
    void shouldApplyUnifiedTimeoutInsteadOfNeverTimingOut() {
        SseEmitter emitter = service.createStream(1L);
        assertEquals(properties.getTimeoutMs(), emitter.getTimeout());
    }

    @Test
    void shouldRejectWhenTelemetryConnectionLimitReached() {
        properties.setMaxConnections(1);
        service.createStream(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createStream(1L));
        assertEquals("SSE_CONNECTION_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(1.0, registry.get("dispatchflow.sse.telemetry.connections.rejected").counter().count());
    }

    @Test
    void heartbeatShouldRemoveBrokenEmittersAndCountClosure() {
        CapturingEmitter healthy = new CapturingEmitter();
        CapturingEmitter broken = new CapturingEmitter();
        broken.broken = true;
        service.registerEmitterForTest(healthy, 1L);
        service.registerEmitterForTest(broken, 1L);
        assertEquals(2, service.getActiveConnectionCount());

        service.heartbeat();

        // 断连发射器被移除并计入 closed；健康发射器保留且收到 ping 帧
        assertEquals(1, service.getActiveConnectionCount());
        assertEquals(1.0, registry.get("dispatchflow.sse.telemetry.connections.closed").counter().count());
        assertEquals(1, healthy.sendCount);
    }
}