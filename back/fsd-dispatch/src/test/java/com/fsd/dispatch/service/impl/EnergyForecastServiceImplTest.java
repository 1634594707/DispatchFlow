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
import com.fsd.dispatch.service.EnergyForecastService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ALG-FC 回归：补能需求预测的读取、回退与安全优先边界。
 *
 * <p>核心契约：预测缺失/超期 → 行为与接入前一致（无压力、不推迟返充）；
 * SOC 余量不足 → 安全优先，无论预测多么拥挤都立即返充。
 */
@ExtendWith(MockitoExtension.class)
class EnergyForecastServiceImplTest {

    @Mock
    private EnergyForecastMapper energyForecastMapper;

    private EnergyForecastProperties properties;
    private FleetEnergyProperties fleetEnergyProperties;
    private EnergyForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new EnergyForecastProperties();
        fleetEnergyProperties = new FleetEnergyProperties();
        service = new EnergyForecastServiceImpl(properties, fleetEnergyProperties, energyForecastMapper);
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
        when(energyForecastMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                row(101L, 5.0, 7.0, 6.0, LocalDateTime.now())));

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
