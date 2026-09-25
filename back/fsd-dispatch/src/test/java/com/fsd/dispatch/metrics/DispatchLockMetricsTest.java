package com.fsd.dispatch.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 §7.3：锁指标必须真的注册进 MeterRegistry，而不只是被健康面板读。
 * 此前这里是裸 AtomicLong —— 注册缺失时下面两次 registry.get 都会抛，测试即失败。
 */
class DispatchLockMetricsTest {

    private MeterRegistry registry;
    private DispatchLockMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DispatchLockMetrics(registry);
    }

    @Test
    void bothMetersShouldBeRegisteredEvenBeforeFirstRecord() {
        // registry.get(...) 在仪表不存在时直接抛，注册缺失（本项原来的毛病）会让这条测不过
        assertEquals(0D, registry.get("dispatchflow.dispatch.lock.acquire.failed").counter().count());
        assertEquals(0L, registry.get("dispatchflow.dispatch.lock.held").timer().count());
    }

    @Test
    void healthPanelGettersShouldReadBackFromTheSameMetersPrometheusSees() {
        metrics.recordAcquireFailure();
        metrics.recordHeldDuration(Duration.ofMillis(120));
        metrics.recordHeldDuration(Duration.ofMillis(380));

        assertEquals(1L, metrics.getAcquireFailureCount());
        assertEquals(1D, registry.get("dispatchflow.dispatch.lock.acquire.failed").counter().count());

        assertEquals(2L, metrics.getHeldDurationCount());
        assertEquals(380L, metrics.getMaxHeldDurationMs());
        assertEquals(250L, metrics.getAverageHeldDurationMs());
        assertEquals(2L, registry.get("dispatchflow.dispatch.lock.held").timer().count());
    }

    @Test
    void emptyTimerShouldNotProduceDivideByZeroOrNegativeAverage() {
        assertEquals(0L, metrics.getAverageHeldDurationMs());
        assertEquals(0L, metrics.getMaxHeldDurationMs());
    }
}
