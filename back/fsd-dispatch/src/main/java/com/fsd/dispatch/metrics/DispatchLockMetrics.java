package com.fsd.dispatch.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class DispatchLockMetrics {

    private final AtomicLong acquireFailureCount = new AtomicLong();
    private final AtomicLong heldDurationCount = new AtomicLong();
    private final AtomicLong heldDurationTotalMs = new AtomicLong();
    private final AtomicLong heldDurationMaxMs = new AtomicLong();

    public void recordAcquireFailure() {
        acquireFailureCount.incrementAndGet();
    }

    public void recordHeldDuration(Duration duration) {
        long durationMs = Math.max(0L, duration.toMillis());
        heldDurationCount.incrementAndGet();
        heldDurationTotalMs.addAndGet(durationMs);
        heldDurationMaxMs.accumulateAndGet(durationMs, Math::max);
    }

    public long getAcquireFailureCount() {
        return acquireFailureCount.get();
    }

    public long getHeldDurationCount() {
        return heldDurationCount.get();
    }

    public long getAverageHeldDurationMs() {
        long count = heldDurationCount.get();
        return count == 0 ? 0L : heldDurationTotalMs.get() / count;
    }

    public long getMaxHeldDurationMs() {
        return heldDurationMaxMs.get();
    }
}
