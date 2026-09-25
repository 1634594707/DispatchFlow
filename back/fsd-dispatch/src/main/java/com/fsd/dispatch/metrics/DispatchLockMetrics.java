package com.fsd.dispatch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 派单任务锁指标（路线图 §7.3）。
 *
 * <p>计数与耗时都以 Micrometer 仪表为唯一存储：此前是 4 个裸 {@code AtomicLong}，只被
 * {@code SystemHealthAdminServiceImpl} 的面板读，Prometheus 侧看不到 ⇒ 锁冲突率与持锁时长在真实
 * 监控里等于不存在。健康面板要的三个数直接由 Timer 派生（count / max / total÷count），不再另存一份。</p>
 */
@Component
public class DispatchLockMetrics {

    private final Counter acquireFailure;
    private final Timer heldDuration;

    public DispatchLockMetrics(MeterRegistry registry) {
        acquireFailure = Counter.builder("dispatchflow.dispatch.lock.acquire.failed")
                .description("Task lock acquisitions rejected because the task is already locked")
                .register(registry);
        heldDuration = Timer.builder("dispatchflow.dispatch.lock.held")
                .description("How long dispatch task locks are held")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordAcquireFailure() {
        acquireFailure.increment();
    }

    public void recordHeldDuration(Duration duration) {
        long durationMs = Math.max(0L, duration.toMillis());
        heldDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public long getAcquireFailureCount() {
        return (long) acquireFailure.count();
    }

    public long getHeldDurationCount() {
        return heldDuration.count();
    }

    public long getAverageHeldDurationMs() {
        long count = heldDuration.count();
        if (count == 0) {
            return 0L;
        }
        long totalMs = TimeUnit.NANOSECONDS.toMillis((long) heldDuration.totalTime(TimeUnit.NANOSECONDS));
        return totalMs / count;
    }

    public long getMaxHeldDurationMs() {
        return (long) heldDuration.max(TimeUnit.MILLISECONDS);
    }
}
