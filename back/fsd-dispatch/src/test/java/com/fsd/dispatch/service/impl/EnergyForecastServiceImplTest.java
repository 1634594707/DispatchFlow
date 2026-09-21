package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.dispatch.config.EnergyForecastProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.EnergyForecastEntity;
import com.fsd.dispatch.mapper.EnergyForecastMapper;
import com.fsd.dispatch.metrics.EnergyForecastMetrics;
import com.fsd.dispatch.service.EnergyForecastService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ALG-FC 回归：补能需求预测的读取、回退与安全优先边界。
 *
 * <p>核心契约：预测缺失/超期 → 行为与接入前一致（无压力、不推迟返充）；
 * SOC 余量不足 → 安全优先，无论预测多么拥挤都立即返充。
 *
 * <p>第二层契约（M4 第 3 条）：行为一致**不等于**原因相同。退化必须被分类成
 * {@link EnergyForecastService.ForecastAvailability} 的具体一档并留下指标，
 * 否则"日作业没跑"和"凌晨确实空闲"在运维上无法区分。
 */
@ExtendWith(MockitoExtension.class)
class EnergyForecastServiceImplTest {

    @Mock
    private EnergyForecastMapper energyForecastMapper;

    private EnergyForecastProperties properties;
    private FleetEnergyProperties fleetEnergyProperties;
    private SimpleMeterRegistry registry;
    private EnergyForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new EnergyForecastProperties();
        fleetEnergyProperties = new FleetEnergyProperties();
        registry = new SimpleMeterRegistry();
        service = new EnergyForecastServiceImpl(properties, fleetEnergyProperties, energyForecastMapper,
                new EnergyForecastMetrics(registry));
    }

    @Test
    void shouldReturnEmptyWhenForecastTableIsEmpty() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        LocalDate today = LocalDate.now();
        assertTrue(service.currentHourForecast(today, 1L, 101L).isEmpty(), "无预测数据应返回空");
        assertEquals(0D, service.parkPressure(today, 1L), 1e-9, "无预测数据压力为 0");
        assertFalse(service.shouldDeferReturnToCharge(today, 1L, 40),
                "无预测数据不得推迟返充（回退纯阈值策略）");
    }

    @Test
    void shouldReadCurrentHourForecastForStation() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 2.5, 4.0, 1.2, LocalDateTime.now()),
                row(202L, 9.9, 12.0, 8.0, LocalDateTime.now())));

        Optional<EnergyForecastService.StationDemandForecast> forecast =
                service.currentHourForecast(LocalDate.now(), 1L, 101L);

        assertTrue(forecast.isPresent());
        assertEquals(101L, forecast.get().stationId());
        assertEquals(2.5, forecast.get().demandP50().doubleValue(), 1e-6);
        assertEquals(4.0, forecast.get().demandP90().doubleValue(), 1e-6);
        assertEquals(1.2, forecast.get().pressureP95().doubleValue(), 1e-6);
        assertEquals("test-model-v1", forecast.get().modelVersion());
    }

    @Test
    void parkPressureShouldTakeMaximumAcrossStations() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 2.5, 4.0, 1.2, LocalDateTime.now()),
                row(202L, 9.9, 12.0, 8.0, LocalDateTime.now())));

        assertEquals(8.0, service.parkPressure(LocalDate.now(), 1L), 1e-6,
                "园区压力取各站 pressureP95 最大值");
    }

    @Test
    void shouldIgnoreStaleForecastBeyondMaxAge() {
        properties.setMaxDataAgeHours(4);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 9.0, 12.0, 9.5, LocalDateTime.now().minusHours(30))));

        assertEquals(0D, service.parkPressure(LocalDate.now(), 1L), 1e-9, "超期预测应失效");
        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 60));
    }

    @Test
    void shouldDeferReturnToChargeOnlyWhenSocHasSafetyMargin() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(peakyDay(6.0, 1.0));

        // criticalSoc=5、deferMargin=10 → 15 及以上才有错峰空间
        assertTrue(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 30),
                "压力高 + SOC 有余量 → 推迟返充");
        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 15),
                "SOC 恰好等于 临界+余量 时不推迟");
        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 8),
                "接近临界 SOC 必须立即返充（安全优先）");
    }

    @Test
    void shouldNotDeferWhenPressureBelowThreshold() {
        properties.setPressureThreshold(5.0D);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 2.0, 3.0, 4.9, LocalDateTime.now())));

        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 80),
                "压力低于阈值不触发错峰");
    }

    @Test
    void shouldIgnoreForecastRowsOfOtherHours() {
        EnergyForecastEntity otherHour = row(101L, 9.0, 12.0, 9.0, LocalDateTime.now());
        otherHour.setHourOfDay((LocalDateTime.now().getHour() + 3) % 24);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(otherHour));

        assertEquals(0D, service.parkPressure(LocalDate.now(), 1L), 1e-9,
                "只消费当前小时的预测行");
    }

    @Test
    void shouldRespectGlobalSwitch() {
        properties.setEnabled(false);
        assertEquals(0D, service.parkPressure(LocalDate.now(), 1L), 1e-9);
        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 90));
    }

    @Test
    void shouldNotDeferWhenDeferSwitchDisabled() {
        properties.setDeferReturnEnabled(false);
        // lenient：开关关闭时不会查库，这里铺数据是为了证明"拦住推迟的是开关本身，而不是没有预测数据"
        org.mockito.Mockito.lenient()
                .when(energyForecastMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(row(101L, 9.0, 12.0, 9.0, LocalDateTime.now())));

        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 90),
                "错峰开关关闭时保持纯阈值行为");
    }

    // ------------------------------------------------------------------ //
    // M4 第 3 条：退化必须分类并留下信号，不能只剩一个 0
    // ------------------------------------------------------------------ //

    @Test
    void emptyForecastTableShouldReportNoRowsRatherThanJustZero() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(EnergyForecastService.ForecastAvailability.NO_ROWS, status.availability());
        assertFalse(status.usable());
        assertEquals(0D, status.pressure(), 1e-9, "压力仍为 0：加诊断不得改变行为");
        assertTrue(status.explain().contains("日作业"), "说明要直接点出根因，实际=" + status.explain());
    }

    @Test
    void staleRowsShouldBeClassifiedBeforeMissingHour() {
        // 日作业停摆 30 小时：当天有行但全部超期，同时也不覆盖当前小时。根因是停摆，不是剖面不完整
        properties.setMaxDataAgeHours(4);
        EnergyForecastEntity otherHour = row(101L, 9.0, 12.0, 9.0, LocalDateTime.now().minusHours(30));
        otherHour.setHourOfDay((LocalDateTime.now().getHour() + 5) % 24);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(otherHour));

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(EnergyForecastService.ForecastAvailability.STALE, status.availability(),
                "超期须优先于缺小时，否则停摆会被误报成剖面不完整");
        assertEquals(1, status.rowsForDate());
        assertEquals(0, status.rowsForHour());
        assertEquals(0D, status.pressure(), 1e-9);
        assertTrue(status.explain().contains("停摆"), "实际=" + status.explain());
    }

    @Test
    void freshRowsOfOtherHoursShouldBeClassifiedSeparatelyFromStale() {
        EnergyForecastEntity otherHour = row(101L, 9.0, 12.0, 9.0, LocalDateTime.now().minusMinutes(5));
        otherHour.setHourOfDay((LocalDateTime.now().getHour() + 5) % 24);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(otherHour));

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(EnergyForecastService.ForecastAvailability.NOT_THIS_HOUR, status.availability());
        assertEquals(1, status.rowsForDate());
        assertTrue(status.explain().contains("当前小时"), "实际=" + status.explain());
    }

    @Test
    void freshForecastShouldReportPressureAndAgreeWithParkPressure() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 2.5, 4.0, 1.2, LocalDateTime.now()),
                row(202L, 9.9, 12.0, 8.0, LocalDateTime.now())));

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(EnergyForecastService.ForecastAvailability.FRESH, status.availability());
        assertTrue(status.usable());
        assertEquals(2, status.rowsForHour());
        assertEquals(8.0, status.pressure(), 1e-6);
        assertEquals(status.pressure(), service.parkPressure(LocalDate.now(), 1L), 1e-9,
                "两个入口必须同口径，不能各算一套");
    }

    @Test
    void disabledSwitchShouldClassifyWithoutTouchingDatabase() {
        properties.setEnabled(false);

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(EnergyForecastService.ForecastAvailability.DISABLED, status.availability());
        Mockito.verifyNoInteractions(energyForecastMapper);
    }

    @Test
    void deferGateShouldLeaveAnObservableTraceWhenForecastIsMissing() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 60));

        assertEquals(1D, availabilityCount("NO_ROWS", "1"), 1e-9,
                "预测缺失必须留下指标点：否则这段接线在线上永远是静默的（M4 第 3 条要修的就是这个）");
    }

    @Test
    void safetyFloorShouldNotBeCountedAsForecastFallback() {
        // SOC 已在临界+余量内（5+10）：这是安全侧的正常行为，不该污染"预测不可用"的告警口径
        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 15));

        assertEquals(0D, availabilityCount("NO_ROWS", "1"), 1e-9);
        Mockito.verifyNoInteractions(energyForecastMapper);
    }

    @Test
    void deferGateShouldRecordFreshStateWhenItActuallyDefers() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(peakyDay(6.0, 1.0));

        assertTrue(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 30));

        assertEquals(1D, availabilityCount("FRESH", "1"), 1e-9);
    }

    @Test
    void flatProfileMustNotDeferEvenThoughAbsoluteThresholdPasses() {
        // 复现真实风险：2026-09-17 那份剖面的 pressure_p95 是 152.7~159.85 的平地，
        // 绝对阈值 2.0 对它恒成立。没有相对 prominence 判据时，日作业一旦被调度起来，
        // 错峰返充就从"无操作"翻成"整天推迟"，唯一兜底只剩 SOC 临界阈值。
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(flatDay(159.85, 156.1));

        assertFalse(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 80),
                "剖面太平就不算高峰：宁可推迟不生效，也不能整天不返充");
        assertEquals(1D, availabilityCount("FRESH", "1"), 1e-9, "可用性是 FRESH，问题在判据不在数据");
        assertEquals(1D, flatProfileCount("1"), 1e-9,
                "这一档必须单独留痕，否则\"没触发推迟\"会被读成\"错峰策略在工作\"");
    }

    @Test
    void flatProfileProminenceMustBeReportedNotHidden() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(flatDay(159.85, 156.1));

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);

        assertEquals(156.1, status.dayMedianPressure(), 1e-6);
        assertEquals(159.85 / 156.1, status.peakRatio(), 1e-6);
        assertTrue(status.peakRatio() < 1.5D, "实测峰谷比只有 1.02，正是判据失去区分力的量级");
    }

    @Test
    void zeroBaselineHoursShouldCountAsTheSharpestPeak() {
        // 一天里大多数小时到不了站（中位=0）不是"没有高峰"，那是最尖锐的高峰
        int now = LocalDateTime.now().getHour();
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rowAt(101L, now, 5.0, 5.0, 5.0, LocalDateTime.now(), "m1"),
                rowAt(101L, (now + 1) % 24, 0.0, 0.0, 0.0, LocalDateTime.now(), "m1"),
                rowAt(101L, (now + 2) % 24, 0.0, 0.0, 0.0, LocalDateTime.now(), "m1")));

        EnergyForecastService.ForecastStatus status = service.parkForecastStatus(LocalDate.now(), 1L);
        assertEquals(0D, status.dayMedianPressure(), 1e-9);
        assertTrue(status.peakRatio() == Double.POSITIVE_INFINITY,
                "中位为 0 而当前小时非 0 时必须算作最尖锐的高峰，而不是" + status.peakRatio());

        assertTrue(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 80));
    }

    @Test
    void prominenceGateMustBeDisableableToRestorePureAbsoluteThreshold() {
        properties.setMinPeakPressureRatio(0D);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(flatDay(159.85, 156.1));

        assertTrue(service.shouldDeferReturnToCharge(LocalDate.now(), 1L, 80),
                "min-peak-pressure-ratio<=0 即退回纯绝对阈值，这是回滚开关");
    }

    /** 当前小时是高峰、其余小时是平段，使 prominence 明显大于 1.5。 */
    private static List<EnergyForecastEntity> peakyDay(double peak, double baseline) {
        int now = LocalDateTime.now().getHour();
        return List.of(
                rowAt(101L, now, peak, peak, peak, LocalDateTime.now(), "m1"),
                rowAt(101L, (now + 1) % 24, baseline, baseline, baseline, LocalDateTime.now(), "m1"),
                rowAt(101L, (now + 2) % 24, baseline, baseline, baseline, LocalDateTime.now(), "m1"));
    }

    /** 压测夹具那种"全天一个水平"的剖面：当前小时只比中位高 2.4%。 */
    private static List<EnergyForecastEntity> flatDay(double current, double baseline) {
        int now = LocalDateTime.now().getHour();
        List<EnergyForecastEntity> rows = new java.util.ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            double pressure = hour == now ? current : baseline;
            rows.add(rowAt(101L, hour, pressure, pressure, pressure, LocalDateTime.now(), "m1"));
        }
        return rows;
    }

    private double flatProfileCount(String park) {
        io.micrometer.core.instrument.Counter counter = registry
                .find("dispatchflow.energy_forecast.flat_profile")
                .tag("park", park).counter();
        return counter == null ? 0D : counter.count();
    }

    private double availabilityCount(String state, String park) {
        io.micrometer.core.instrument.Counter counter = registry
                .find("dispatchflow.energy_forecast.availability")
                .tag("state", state).tag("park", park).counter();
        return counter == null ? 0D : counter.count();
    }

    // ------------------------------------------------------------------ //
    // 24 小时剖面（管理端可视化，T-03）
    // ------------------------------------------------------------------ //

    @Test
    void parkHourlyProfilesShouldReturnFullDaySortedByHour() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rowAt(101L, 5, 2.0, 3.0, 1.5, LocalDateTime.now(), "m1"),
                rowAt(101L, 1, 1.0, 2.0, 0.5, LocalDateTime.now(), "m1"),
                rowAt(202L, 3, 9.0, 11.0, 8.0, LocalDateTime.now(), "m1")));

        List<EnergyForecastService.StationHourlyProfile> profiles =
                service.parkHourlyProfiles(LocalDate.now(), 1L);

        assertEquals(2, profiles.size(), "按站点聚合");
        EnergyForecastService.StationHourlyProfile first = profiles.get(0);
        assertEquals(101L, first.stationId());
        assertEquals(List.of(1, 5), first.hours().stream()
                        .map(EnergyForecastService.HourlyDemandPoint::hourOfDay).toList(),
                "小时应按升序返回");
        assertFalse(first.stale(), "刚生成的预测不应标记为失效");
        assertEquals("m1", first.modelVersion());
        assertEquals(120, first.sampleCount());
    }

    @Test
    void parkHourlyProfilesShouldKeepNewestRowPerHour() {
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rowAt(101L, 8, 1.0, 1.5, 1.0, LocalDateTime.now().minusHours(3), "old"),
                rowAt(101L, 8, 7.0, 9.0, 6.0, LocalDateTime.now(), "new")));

        List<EnergyForecastService.StationHourlyProfile> profiles =
                service.parkHourlyProfiles(LocalDate.now(), 1L);

        assertEquals(1, profiles.size());
        EnergyForecastService.StationHourlyProfile profile = profiles.get(0);
        assertEquals(1, profile.hours().size(), "同一小时只保留一条");
        assertEquals("new", profile.modelVersion(), "应取 generatedAt 最新的一行（与派单侧口径一致）");
        assertEquals(7.0, profile.hours().get(0).demandP50().doubleValue(), 1e-6);
    }

    @Test
    void parkHourlyProfilesShouldFlagStaleButStillReturnRows() {
        properties.setMaxDataAgeHours(4);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                rowAt(101L, 2, 1.0, 2.0, 1.0, LocalDateTime.now().minusHours(30), "old")));

        List<EnergyForecastService.StationHourlyProfile> profiles =
                service.parkHourlyProfiles(LocalDate.now(), 1L);

        assertEquals(1, profiles.size(), "超期数据仍返回，交由前端如实展示而不得隐藏");
        assertTrue(profiles.get(0).stale(), "超出 max-data-age-hours 应标记失效");
    }

    @Test
    void parkHourlyProfilesShouldReturnEmptyWhenDisabledOrMissing() {
        properties.setEnabled(false);
        assertTrue(service.parkHourlyProfiles(LocalDate.now(), 1L).isEmpty(), "开关关闭返回空");

        properties.setEnabled(true);
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.parkHourlyProfiles(LocalDate.now(), 1L).isEmpty(), "无数据返回空");
    }

    private static EnergyForecastEntity rowAt(Long stationId, int hour, double p50, double p90,
                                              double pressure, LocalDateTime generatedAt,
                                              String modelVersion) {
        EnergyForecastEntity entity = row(stationId, p50, p90, pressure, generatedAt);
        entity.setHourOfDay(hour);
        entity.setForecastDate(LocalDate.now());
        entity.setModelVersion(modelVersion);
        return entity;
    }

    private static EnergyForecastEntity row(Long stationId, double p50, double p90, double pressure,
                                            LocalDateTime generatedAt) {
        EnergyForecastEntity entity = new EnergyForecastEntity();
        entity.setParkId(1L);
        entity.setStationId(stationId);
        entity.setStationCode("S" + stationId);
        entity.setForecastDate(LocalDate.now());
        entity.setHourOfDay(LocalDateTime.now().getHour());
        entity.setDemandP50(BigDecimal.valueOf(p50));
        entity.setDemandP90(BigDecimal.valueOf(p90));
        entity.setPressureP95(BigDecimal.valueOf(pressure));
        entity.setSampleCount(120);
        entity.setModelVersion("test-model-v1");
        entity.setGeneratedAt(generatedAt);
        entity.setDeleted(0);
        return entity;
    }
}
