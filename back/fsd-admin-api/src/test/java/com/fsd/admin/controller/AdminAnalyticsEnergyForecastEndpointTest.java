package com.fsd.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.auth.AdminPermissionService;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastHourPoint;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastStationItem;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.model.ApiResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * T-03：管理端补能预测端点的 HTTP 层契约。
 *
 * <p>前端 {@code ChargingReport.vue} / {@code EnergyForecastPanel.vue} 直接读取这些字段名，
 * 因此这里用真实 Jackson 序列化锁死字段名——改名字会让页面静默显示空值，必须有测试兜住。
 */
class AdminAnalyticsEnergyForecastEndpointTest {

    private AnalyticsAdminService analyticsAdminService;
    private AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        analyticsAdminService = mock(AnalyticsAdminService.class);
        controller = new AdminAnalyticsController(analyticsAdminService, new AdminPermissionService());
    }

    @Test
    void anonymousRequestShouldBeRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.energyForecast(null, 1L, request));
        assertEquals("ADMIN_AUTH_REQUIRED", ex.getCode());
    }

    @Test
    void shouldForwardDateAndParkToService() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        when(analyticsAdminService.getEnergyForecast(eq(date), eq(7L)))
                .thenReturn(emptyResponse(date, 7L));

        ApiResponse<AdminAnalyticsEnergyForecastResponse> response =
                controller.energyForecast(date, 7L, authenticatedRequest());

        assertTrue(response.isSuccess());
        assertEquals("SUCCESS", response.getCode());
        assertNotNull(response.getData());
        verify(analyticsAdminService).getEnergyForecast(date, 7L);
    }

    @Test
    void shouldForwardNullsWhenParamsMissing() {
        when(analyticsAdminService.getEnergyForecast(isNull(), isNull()))
                .thenReturn(emptyResponse(LocalDate.now(), null));

        controller.energyForecast(null, null, authenticatedRequest());

        verify(analyticsAdminService).getEnergyForecast(isNull(), isNull());
    }

    @Test
    void serializedShapeShouldMatchFrontendContract() throws Exception {
        AdminAnalyticsEnergyForecastResponse payload = AdminAnalyticsEnergyForecastResponse.builder()
                .enabled(true)
                .pressureThreshold(2.0D)
                .maxDataAgeHours(24)
                .forecastDate(LocalDate.of(2026, 9, 17))
                .parkId(1L)
                .serverTime(LocalDateTime.of(2026, 9, 17, 1, 31, 0))
                .stationCount(1)
                .anyData(true)
                .anyStale(true)
                .stations(List.of(AdminAnalyticsEnergyForecastStationItem.builder()
                        .parkId(1L)
                        .stationId(510L)
                        .stationCode("ZJF-CHG-01")
                        .modelVersion("energy-demand-xgboost-zjf-chg01-20260917")
                        .generatedAt(LocalDateTime.of(2026, 9, 17, 1, 24, 20))
                        .stale(true)
                        .sampleCount(288)
                        .hourCount(1)
                        .peakPressureP95(new BigDecimal("162.85"))
                        .peakHourOfDay(14)
                        .peakDemandP90(new BigDecimal("20.5"))
                        .pressureThresholdExceeded(true)
                        .hours(List.of(AdminAnalyticsEnergyForecastHourPoint.builder()
                                .hourOfDay(14)
                                .demandP50(new BigDecimal("8.0"))
                                .demandP90(new BigDecimal("20.5"))
                                .pressureP95(new BigDecimal("162.85"))
                                .build()))
                        .build()))
                .build();
        when(analyticsAdminService.getEnergyForecast(isNull(), eq(1L))).thenReturn(payload);

        ApiResponse<AdminAnalyticsEnergyForecastResponse> response =
                controller.energyForecast(null, 1L, authenticatedRequest());

        String json = serializeWithBootJackson(response.getData());

        // 顶层字段（前端 types/analytics.d.ts:AnalyticsEnergyForecast）
        for (String key : List.of("enabled", "pressureThreshold", "maxDataAgeHours", "forecastDate",
                "parkId", "serverTime", "stationCount", "anyData", "anyStale", "stations")) {
            assertTrue(json.contains("\"" + key + "\""), "缺少顶层字段: " + key);
        }
        // 站点字段（AnalyticsEnergyForecastStation）
        for (String key : List.of("stationId", "stationCode", "modelVersion", "generatedAt", "stale",
                "sampleCount", "hourCount", "peakPressureP95", "peakHourOfDay", "peakDemandP90",
                "pressureThresholdExceeded", "hours")) {
            assertTrue(json.contains("\"" + key + "\""), "缺少站点字段: " + key);
        }
        // 小时点字段（AnalyticsEnergyForecastHourPoint）
        for (String key : List.of("hourOfDay", "demandP50", "demandP90", "pressureP95")) {
            assertTrue(json.contains("\"" + key + "\""), "缺少小时字段: " + key);
        }
        // 时间必须是 ISO 字符串（前端用 /T(\d{2}):/ 正则直接取服务端小时，不能是数组或时间戳）
        assertTrue(json.contains("\"serverTime\":\"2026-09-17T01:31:00\""), "serverTime 必须为 ISO 字符串: " + json);
        assertTrue(json.contains("\"forecastDate\":\"2026-09-17\""), "forecastDate 必须为 ISO 日期字符串");
        // 阈值必须如实透出，前端据此与峰值并排展示（T-07 可观测化）
        assertTrue(json.contains("\"pressureThreshold\":2.0"), "pressureThreshold 必须为数值原值");
    }

    @Test
    void emptyResponseShouldStillReportEnvelope() {
        when(analyticsAdminService.getEnergyForecast(isNull(), eq(1L)))
                .thenReturn(emptyResponse(LocalDate.of(2026, 9, 17), 1L));

        AdminAnalyticsEnergyForecastResponse data =
                controller.energyForecast(null, 1L, authenticatedRequest()).getData();

        assertFalse(data.isAnyData());
        assertFalse(data.isAnyStale());
        assertEquals(0, data.getStationCount());
        assertTrue(data.getStations().isEmpty());
    }

    /**
     * 用 Spring Boot 真实的 {@code JacksonAutoConfiguration} 序列化，而不是手搓 ObjectMapper。
     *
     * <p>这一点很关键：手搓（`new ObjectMapper()`，甚至裸 `Jackson2ObjectMapperBuilder.json().build()`）
     * 都会保留 `WRITE_DATES_AS_TIMESTAMPS=true`，把 LocalDateTime 写成 `[2026,9,17,1,31]`。
     * 而生产实测（`GET /api/open/v1/orders/12`）返回的是 `"createdAt":"2026-09-15T15:28:01"`。
     * 只有走自动配置，测试断言的才是真实线格式；否则测试会误报，也无法在有人加了
     * `spring.jackson.*` 覆盖时失败。本仓库全量检索确认没有任何 Jackson 自定义配置。
     */
    private String serializeWithBootJackson(Object payload) {
        AtomicReference<String> json = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    json.set(mapper.writeValueAsString(payload));
                });
        assertNotNull(json.get(), "未能从 Boot 自动配置取得 ObjectMapper");
        return json.get();
    }

    private AdminAnalyticsEnergyForecastResponse emptyResponse(LocalDate date, Long parkId) {
        return AdminAnalyticsEnergyForecastResponse.builder()
                .enabled(true)
                .pressureThreshold(2.0D)
                .maxDataAgeHours(24)
                .forecastDate(date)
                .parkId(parkId)
                .serverTime(LocalDateTime.now())
                .stationCount(0)
                .anyData(false)
                .anyStale(false)
                .stations(List.of())
                .build();
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, AdminRole.ADMIN.name());
        request.setAttribute(AdminAuthSupport.ADMIN_USER_ID_ATTRIBUTE, 1L);
        request.setAttribute(AdminAuthSupport.ADMIN_USERNAME_ATTRIBUTE, "admin");
        return request;
    }
}
