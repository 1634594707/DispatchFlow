package com.fsd.dispatch.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 阶段六 6.1/6.2 单元测试：验证围栏响应级别分级与按围栏 buffer_meters 配置。
 */
@ExtendWith(MockitoExtension.class)
class GeofenceBreachServiceImplTest {

    @Mock
    private ParkGeofenceMapper geofenceMapper;
    @Mock
    private DispatchExceptionService dispatchExceptionService;
    @Mock
    private DispatchAutomationRuleService automationRuleService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private DispatchTaskMapper dispatchTaskMapper;

    @InjectMocks
    private GeofenceBreachServiceImpl geofenceBreachService;

    /** 正方形围栏多边形（GCJ-02）。 */
    private static final String SQUARE_POLYGON_JSON =
            "[[121.05,31.90],[121.07,31.90],[121.07,31.92],[121.05,31.92]]";
    /** 围栏外的点（触发 BOUNDARY → GEOFENCE_EXIT）。 */
    private static final BigDecimal OUTSIDE_LNG = new BigDecimal("121.090000");
    private static final BigDecimal OUTSIDE_LAT = new BigDecimal("31.910000");

    @BeforeEach
    void setUp() {
        // 注入 defaultParkId（@Value 字段）
        ReflectionTestUtils.setField(geofenceBreachService, "defaultParkId", 1L);
    }

    @Test
    void infoLevelShouldOnlyLogAndNotRecordException() {
        // INFO 级别：仅记录日志，不写异常、不触发自动化规则、不紧急停车。
        // 注意：INFO 路径在 findCurrentTaskId 之前 return，故无需桩定 dispatchTaskMapper。
        ParkGeofenceEntity fence = buildFence("WARN", null);
        fence.setResponseLevel("INFO");
        setupFenceQuery(fence);
        setupFirstBreach(true);

        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), OUTSIDE_LNG, OUTSIDE_LAT);

        verify(dispatchExceptionService, never()).recordException(anyLong(), any(), anyLong(), anyString(), anyString());
        verify(dispatchExceptionService, never()).recordVehicleException(anyLong(), anyString(), anyString());
        verify(automationRuleService, never()).evaluateGeofenceBreach(anyLong(), any(), anyString(), anyString());
        verify(vehicleService, never()).markUnavailable(anyLong());
    }

    @Test
    void warnLevelShouldRecordExceptionButNotEmergencyStop() {
        // WARN 级别：记录异常 + 触发自动化规则，但不触发紧急停车。
        ParkGeofenceEntity fence = buildFence("WARN", null);
        setupFenceQuery(fence);
        setupFirstBreach(true);
        setupBusyVehicleWithTask();

        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), OUTSIDE_LNG, OUTSIDE_LAT);

        verify(dispatchExceptionService).recordException(eq(5001L), eq(null), eq(9001L), eq("GEOFENCE_EXIT"), anyString());
        verify(automationRuleService).evaluateGeofenceBreach(eq(1L), any(VehicleEntity.class), eq("TEST-FENCE"), eq("GEOFENCE_EXIT"));
        verify(vehicleService, never()).markUnavailable(anyLong());
    }

    @Test
    void blockLevelShouldTriggerEmergencyStop() {
        // BLOCK 级别：记录异常 + 触发自动化规则 + 触发紧急停车（markUnavailable）。
        ParkGeofenceEntity fence = buildFence("WARN", null);
        fence.setResponseLevel("BLOCK");
        setupFenceQuery(fence);
        setupFirstBreach(true);
        setupBusyVehicleWithTask();

        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), OUTSIDE_LNG, OUTSIDE_LAT);

        verify(dispatchExceptionService).recordException(eq(5001L), eq(null), eq(9001L), eq("GEOFENCE_EXIT"), anyString());
        verify(automationRuleService).evaluateGeofenceBreach(eq(1L), any(VehicleEntity.class), eq("TEST-FENCE"), eq("GEOFENCE_EXIT"));
        verify(vehicleService).markUnavailable(eq(9001L));
    }

    @Test
    void perFenceBufferMetersShouldSuppressExitWhenWithinBuffer() {
        // 6.2：buffer_meters=10000 米（10km），围栏外但仍在缓冲内的点应被抑制，不记录异常。
        ParkGeofenceEntity fence = buildFence("WARN", new BigDecimal("10000"));
        setupFenceQuery(fence);
        setupFirstBreach(true);

        // 点在围栏外但距边界约 1.7km（< 10km 缓冲），应被 GEOFENCE_EXIT 缓冲抑制。
        // 121.080 vs 边界 121.07，约 0.01° ~ 0.9km 经距 * cos(31.91°) ≈ 0.77km < 10km。
        BigDecimal nearOutsideLng = new BigDecimal("121.080000");
        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), nearOutsideLng, OUTSIDE_LAT);

        verify(dispatchExceptionService, never()).recordException(anyLong(), any(), anyLong(), anyString(), anyString());
        verify(dispatchExceptionService, never()).recordVehicleException(anyLong(), anyString(), anyString());
        verify(vehicleService, never()).markUnavailable(anyLong());
    }

    // ==================== 阶段九 9.2 围栏检测回归测试 ====================

    /**
     * 9.2：车辆位于多边形中心（明显在围栏内），不应触发 GEOFENCE_EXIT。
     * 正方形围栏 [[121.05,31.90],[121.07,31.90],[121.07,31.92],[121.05,31.92]] 中心为 (121.06, 31.91)。
     * inside=true → breachType=null → evaluateFence 提前返回，不调用 recordException。
     */
    @Test
    void insideFenceShouldNotTriggerBreach() {
        ParkGeofenceEntity fence = buildFence("WARN", null);
        setupFenceQuery(fence);

        // 多边形中心点，明显在围栏内
        BigDecimal centerLng = new BigDecimal("121.060000");
        BigDecimal centerLat = new BigDecimal("31.910000");
        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), centerLng, centerLat);

        verify(dispatchExceptionService, never()).recordException(anyLong(), any(), anyLong(), anyString(), anyString());
        verify(dispatchExceptionService, never()).recordVehicleException(anyLong(), anyString(), anyString());
        verify(vehicleService, never()).markUnavailable(anyLong());
    }

    /**
     * 9.2：车辆位于东边界外侧 1mm 量级处（121.070001, 31.91），SHOULD 触发 GEOFENCE_EXIT。
     *
     * <p>注意：{@code resolveBufferMeters} 对 {@code BigDecimal.ZERO} 会回退到 15m 默认缓冲
     * （因 {@code 0 > 0} 为 false）。若用 ZERO/默认缓冲，0.000001° ≈ 0.094m 的偏差会被抑制。
     * 故此处使用 1mm 缓冲（{@code new BigDecimal("0.001")}），确保偏差 0.094m > 0.001m 触发告警。
     */
    @Test
    void boundaryPointJustOutsideShouldTriggerBreach() {
        // 1mm 缓冲：v > 0 为 true，返回 0.001m；0.094m 偏差 > 0.001m → 不在缓冲内 → 触发告警
        ParkGeofenceEntity fence = buildFence("WARN", new BigDecimal("0.001"));
        setupFenceQuery(fence);
        setupFirstBreach(true);
        setupBusyVehicleWithTask();

        // 东边界 121.07 外侧 0.000001°（约 0.094m）
        BigDecimal justOutsideLng = new BigDecimal("121.070001");
        BigDecimal edgeLat = new BigDecimal("31.910000");
        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), justOutsideLng, edgeLat);

        verify(dispatchExceptionService).recordException(eq(5001L), eq(null), eq(9001L), eq("GEOFENCE_EXIT"), anyString());
        verify(automationRuleService).evaluateGeofenceBreach(eq(1L), any(VehicleEntity.class), eq("TEST-FENCE"), eq("GEOFENCE_EXIT"));
    }

    /**
     * 9.2：车辆位于东边界内侧 1mm 量级处（121.069999, 31.91），不应触发 GEOFENCE_EXIT。
     * inside=true → breachType=null → 提前返回。bufferMeters 取值不影响内部点判定。
     */
    @Test
    void boundaryPointJustInsideShouldNotTriggerBreach() {
        ParkGeofenceEntity fence = buildFence("WARN", BigDecimal.ZERO);
        setupFenceQuery(fence);

        // 东边界 121.07 内侧 0.000001°（仍在围栏内）
        BigDecimal justInsideLng = new BigDecimal("121.069999");
        BigDecimal edgeLat = new BigDecimal("31.910000");
        geofenceBreachService.evaluateVehiclePosition(1L, buildBusyVehicle(), justInsideLng, edgeLat);

        verify(dispatchExceptionService, never()).recordException(anyLong(), any(), anyLong(), anyString(), anyString());
        verify(dispatchExceptionService, never()).recordVehicleException(anyLong(), anyString(), anyString());
        verify(vehicleService, never()).markUnavailable(anyLong());
    }

    // ==================== helpers ====================

    private ParkGeofenceEntity buildFence(String responseLevel, BigDecimal bufferMeters) {
        ParkGeofenceEntity fence = new ParkGeofenceEntity();
        fence.setId(100L);
        fence.setParkId(1L);
        fence.setFenceCode("TEST-FENCE");
        fence.setFenceName("测试围栏");
        fence.setFenceType("BOUNDARY");
        fence.setResponseLevel(responseLevel);
        fence.setBufferMeters(bufferMeters);
        fence.setPolygonJson(SQUARE_POLYGON_JSON);
        fence.setStatus("ACTIVE");
        fence.setDeleted(0);
        return fence;
    }

    @SuppressWarnings("unchecked")
    private void setupFenceQuery(ParkGeofenceEntity fence) {
        when(geofenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fence));
    }

    private void setupFirstBreach(boolean firstBreach) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(firstBreach);
    }

    private void setupBusyVehicleWithTask() {
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(5001L);
        Page<DispatchTaskEntity> taskPage = new Page<>();
        taskPage.setRecords(List.of(task));
        when(dispatchTaskMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(taskPage);
    }

    private VehicleEntity buildBusyVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(9001L);
        vehicle.setVehicleCode("V-TEST-001");
        vehicle.setDispatchStatus("BUSY");
        return vehicle;
    }
}
