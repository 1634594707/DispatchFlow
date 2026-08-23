package com.fsd.dispatch.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DispatchOutboxMetricsTest {

    @Test
    void shouldRecordOutboxCountersAndLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DispatchOutboxMetrics metrics = new DispatchOutboxMetrics(registry);

        metrics.claimed(2);
        metrics.published();
        metrics.failed();
        metrics.deadLettered();
        var sample = metrics.startPublishTimer();
        metrics.recordPublishLatency(sample);

        assertEquals(2.0, registry.get("dispatchflow.outbox.events.claimed").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.outbox.events.published").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.outbox.events.failed").counter().count());
        assertEquals(1.0, registry.get("dispatchflow.outbox.events.dead_lettered").counter().count());
        assertEquals(1L, registry.get("dispatchflow.outbox.publish.latency").timer().count());
    }
}
