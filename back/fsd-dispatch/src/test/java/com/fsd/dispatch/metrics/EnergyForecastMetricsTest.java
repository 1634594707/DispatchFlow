package com.fsd.dispatch.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.service.EnergyForecastService.ForecastAvailability;
import com.fsd.dispatch.service.EnergyForecastService.ForecastStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 预测退化的可观测性（路线图 M4 第 3 条）。
 *
 * <p>要守住的性质只有两条：退化每次都进计数器（这样 {@code rate()} 告警才成立），
 * 以及 WARN 有限速（这样它不会在补能规则循环里淹没其他日志）。
 * 两者都是"能被看见"和"看得下去"之间的取舍，所以两边都得钉住。
 */
class EnergyForecastMetricsTest {

    private static final String METRIC = "dispatchflow.energy_forecast.availability";

    private SimpleMeterRegistry registry;
    private EnergyForecastMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new EnergyForecastMetrics(registry);
    }

    @Test
    void everyResolutionShouldBeCountedUnderItsOwnState() {
        metrics.observe(1L, status(ForecastAvailability.NO_ROWS, 1L));
        metrics.observe(1L, status(ForecastAvailability.STALE, 1L));
        metrics.observe(1L, status(ForecastAvailability.FRESH, 1L));

        assertEquals(1D, count("NO_ROWS", "1"), 1e-9);
        assertEquals(1D, count("STALE", "1"), 1e-9);
        assertEquals(1D, count("FRESH", "1"), 1e-9, "可用也要计数：否则无法算出退化占比");
    }

    @Test
    void parksShouldNotShareCounters() {
        metrics.observe(1L, status(ForecastAvailability.NO_ROWS, 1L));
        metrics.observe(2L, status(ForecastAvailability.NO_ROWS, 2L));

        assertEquals(1D, count("NO_ROWS", "1"), 1e-9);
        assertEquals(1D, count("NO_ROWS", "2"), 1e-9);
    }

    @Test
    void warnShouldFireOnceThenBeThrottled() {
        ForecastStatus stale = status(ForecastAvailability.STALE, 1L);

        assertTrue(metrics.observe(1L, stale), "首次退化必须留下 WARN");
        assertFalse(metrics.observe(1L, stale), "同一 (park, state) 在窗口内不得重复刷日志");
        assertFalse(metrics.observe(1L, status(ForecastAvailability.FRESH, 1L)), "可用状态不打 WARN");

        assertEquals(3D, countAllStates("1"), 1e-9, "限速只作用于日志，不得吞掉计数器");
    }

    @Test
    void differentStatesShouldWarnIndependently() {
        // 状态本身变了就是新信息（例如从"缺小时"恶化成"完全没有行"），不该被上一个状态的限速压住
        assertTrue(metrics.observe(1L, status(ForecastAvailability.NOT_THIS_HOUR, 1L)));
        assertTrue(metrics.observe(1L, status(ForecastAvailability.NO_ROWS, 1L)));
    }

    @Test
    void flatProfileShouldHaveItsOwnCounterAndThrottle() {
        ForecastStatus flat = new ForecastStatus(ForecastAvailability.FRESH, 1L, LocalDate.now(),
                LocalDateTime.now().getHour(), 24, 1, LocalDateTime.now(), 24, 159.85D, 156.1D);

        assertTrue(metrics.observeFlatProfile(1L, flat), "判据失去区分力是独立的一档，不能被 FRESH 吸收");
        assertFalse(metrics.observeFlatProfile(1L, flat), "同样受 2 分钟限速约束");

        assertEquals(2D, registry.find(EnergyForecastMetrics.FLAT_PROFILE_METRIC)
                .tag("park", "1").counter().count(), 1e-9);
        assertNull(registry.find(EnergyForecastMetrics.FLAT_PROFILE_METRIC)
                .tag("park", "2").counter(), "计数器按园区分维度，park2 没被查过就不该存在");
    }

    @Test
    void freshStatusShouldNotWarnButStillBeCounted() {
        ForecastStatus fresh = status(ForecastAvailability.FRESH, 1L);

        assertFalse(metrics.observe(1L, fresh));
        assertEquals(1D, count("FRESH", "1"), 1e-9);
    }

    private static ForecastStatus status(ForecastAvailability availability, Long parkId) {
        boolean fresh = availability == ForecastAvailability.FRESH;
        return new ForecastStatus(availability, parkId, LocalDate.now(),
                LocalDateTime.now().getHour(), 24, fresh ? 9 : 0,
                LocalDateTime.now().minusHours(30), 24, fresh ? 8.0D : 0D, fresh ? 2.0D : 0D);
    }

    private double count(String state, String park) {
        Counter counter = registry.find(METRIC).tag("state", state).tag("park", park).counter();
        return counter == null ? 0D : counter.count();
    }

    private double countAllStates(String park) {
        return registry.find(METRIC).tag("park", park).counters().stream()
                .mapToDouble(Counter::count).sum();
    }
}
