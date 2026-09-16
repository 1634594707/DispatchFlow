package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastStationItem;
import com.fsd.dispatch.config.EnergyForecastProperties;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.BatterySwapSessionMapper;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.service.EnergyForecastService;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ALG-FC：管理端补能需求预测只读视图。
 *
 * <p>关键契约（与派单侧口径一致）：
 * <ol>
 *   <li>剖面来自 {@link EnergyForecastService#parkHourlyProfiles}，本层不加"当前小时"过滤</li>
 *   <li>{@code stale} 必须如实透出，不允许被吞掉</li>
 *   <li>{@code parkId} 为空时回落默认园区，而非返回空结果</li>
 * </ol>
 */
class AnalyticsEnergyForecastTest {

    private EnergyForecastService energyForecastService;
    private AdminParkScopeService adminParkScopeService;
    private EnergyForecastProperties properties;
    private AnalyticsAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        energyForecastService = mock(EnergyForecastService.class);
        adminParkScopeService = mock(AdminParkScopeService.class);
        properties = new EnergyForecastProperties();
        service = new AnalyticsAdminServiceImpl(
                mock(OrderMapper.class),
                mock(DispatchTaskMapper.class),
                mock(DispatchExceptionRecordMapper.class),
                mock(ChargingSessionMapper.class),
                mock(BatterySwapSessionMapper.class),
                mock(ChargingPileMapper.class),
                mock(VehicleMapper.class),
                mock(FleetRuntimeService.class),
                mock(ParkMapper.class),
                adminParkScopeService,
                new SimpleMeterRegistry(),
                energyForecastService,
                properties);
    }

    private static EnergyForecastService.StationHourlyProfile profile(Long parkId,
                                                                     Long stationId,
                                                                     String stationCode,
                                                                     boolean stale,
                                                                     LocalDateTime generatedAt,
                                                                     int sampleCount,
                                                                     EnergyForecastService.HourlyDemandPoint... points) {
        return new EnergyForecastService.StationHourlyProfile(
                parkId,
                stationId,
                stationCode,
                LocalDate.now(),
                sampleCount,
                "energy-demand-xgboost-zjf-chg01-20260916",
                generatedAt,
                stale,
                List.of(points));
    }

    private static EnergyForecastService.HourlyDemandPoint point(int hour, String p50, String p90, String pressure) {
        return new EnergyForecastService.HourlyDemandPoint(hour,
                new BigDecimal(p50), new BigDecimal(p90), new BigDecimal(pressure));
    }

    @Test
    void shouldMapProfilesWithPeakAndKeepStaleFlag() {
        when(energyForecastService.isEnabled()).thenReturn(true);
        when(energyForecastService.parkHourlyProfiles(any(LocalDate.class), eq(3L))).thenReturn(List.of(
                profile(3L, 1L, "ZJF-CHG-01", false, LocalDateTime.now().minusHours(1), 510,
                        point(10, "5.0", "9.0", "100.0"),
                        point(14, "8.0", "20.0", "180.0")),
                profile(3L, 2L, "ZJF-CHG-02", true, LocalDateTime.now().minusHours(48), 120,
                        point(14, "1.0", "2.0", "10.0"))));

        AdminAnalyticsEnergyForecastResponse response = service.getEnergyForecast(null, 3L);

        assertTrue(response.isEnabled());
        assertTrue(response.isAnyData());
        assertTrue(response.isAnyStale(), "存在超期站点必须如实上报");
        assertEquals(2, response.getStationCount());
        assertEquals(3L, response.getParkId());
        assertEquals("energy-demand-xgboost-zjf-chg01-20260916",
                response.getStations().get(0).getModelVersion());

        AdminAnalyticsEnergyForecastStationItem fresh = response.getStations().get(0);
        assertEquals(1L, fresh.getStationId());
        assertEquals(2, fresh.getHourCount());
        assertFalse(fresh.isStale());
        assertEquals(0, new BigDecimal("180.0").compareTo(fresh.getPeakPressureP95()));
        assertEquals(14, fresh.getPeakHourOfDay());
        assertEquals(0, new BigDecimal("20.0").compareTo(fresh.getPeakDemandP90()));
        assertEquals(510, fresh.getSampleCount());
        assertTrue(fresh.isPressureThresholdExceeded(), "配置阈值 2.0，峰值 180 必然超过");

        AdminAnalyticsEnergyForecastStationItem stale = response.getStations().get(1);
        assertEquals(2L, stale.getStationId());
        assertTrue(stale.isStale());
        assertEquals(0, new BigDecimal("10.0").compareTo(stale.getPeakPressureP95()));
        assertEquals(14, stale.getPeakHourOfDay());
    }

    @Test
    void shouldFallbackToDefaultParkWhenParkIdMissing() {
        when(energyForecastService.isEnabled()).thenReturn(true);
        when(adminParkScopeService.resolveDefaultParkId()).thenReturn(7L);
        when(energyForecastService.parkHourlyProfiles(any(LocalDate.class), eq(7L))).thenReturn(List.of());

        AdminAnalyticsEnergyForecastResponse response = service.getEnergyForecast(LocalDate.of(2026, 9, 16), null);

        verify(adminParkScopeService).resolveDefaultParkId();
        assertEquals(7L, response.getParkId());
        assertFalse(response.isAnyData());
        assertEquals(0, response.getStationCount());
    }

    @Test
    void shouldReportThresholdAndAgeFromPropertiesWithoutData() {
        properties.setPressureThreshold(3.5D);
        properties.setMaxDataAgeHours(12);
        when(energyForecastService.isEnabled()).thenReturn(true);
        when(energyForecastService.parkHourlyProfiles(any(LocalDate.class), eq(1L))).thenReturn(List.of());

        AdminAnalyticsEnergyForecastResponse response = service.getEnergyForecast(LocalDate.of(2026, 9, 17), 1L);

        assertEquals(3.5D, response.getPressureThreshold());
        assertEquals(12, response.getMaxDataAgeHours());
        assertEquals(LocalDate.of(2026, 9, 17), response.getForecastDate());
        assertTrue(response.getStations().isEmpty());
        assertFalse(response.isAnyStale());
        assertEquals(LocalDate.now(), response.getServerTime().toLocalDate());
    }

    @Test
    void shouldReportDisabledAndEmptyWhenServiceOff() {
        when(energyForecastService.isEnabled()).thenReturn(false);
        when(energyForecastService.parkHourlyProfiles(any(LocalDate.class), eq(1L))).thenReturn(List.of());

        AdminAnalyticsEnergyForecastResponse response = service.getEnergyForecast(null, 1L);

        assertFalse(response.isEnabled());
        assertFalse(response.isAnyData());
        assertTrue(response.getStations().isEmpty());
        assertEquals(LocalDate.now(), response.getForecastDate(), "date 为空取当天");
    }
}
