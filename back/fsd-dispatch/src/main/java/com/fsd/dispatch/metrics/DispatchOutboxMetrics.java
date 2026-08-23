package com.fsd.dispatch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class DispatchOutboxMetrics {

    private final Counter claimed;
    private final Counter published;
    private final Counter failed;
    private final Counter deadLettered;
    private final Timer publishLatency;

    public DispatchOutboxMetrics(MeterRegistry registry) {
        claimed = Counter.builder("dispatchflow.outbox.events.claimed")
                .description("Outbox events claimed for publishing")
                .register(registry);
        published = Counter.builder("dispatchflow.outbox.events.published")
                .description("Outbox events published successfully")
                .register(registry);
        failed = Counter.builder("dispatchflow.outbox.events.failed")
                .description("Outbox publish attempts failed")
                .register(registry);
        deadLettered = Counter.builder("dispatchflow.outbox.events.dead_lettered")
                .description("Outbox events moved to dead letter")
                .register(registry);
        publishLatency = Timer.builder("dispatchflow.outbox.publish.latency")
                .description("Outbox publish latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void claimed(int count) {
        if (count > 0) {
            claimed.increment(count);
        }
    }

    public void published() {
        published.increment();
    }

    public void failed() {
        failed.increment();
    }

    public void deadLettered() {
        deadLettered.increment();
    }

    public Timer.Sample startPublishTimer() {
        return Timer.start();
    }

    public void recordPublishLatency(Timer.Sample sample) {
        sample.stop(publishLatency);
    }
}
