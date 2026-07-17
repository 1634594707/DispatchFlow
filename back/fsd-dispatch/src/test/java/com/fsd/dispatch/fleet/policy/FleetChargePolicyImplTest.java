package com.fsd.dispatch.fleet.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 阶段八 8.4：测试更新为通过 mock StringRedisTemplate（返回 null）让 resolver 回退到 YAML 默认值。
 * 这样原有断言（基于 FleetEnergyProperties 默认值）依然成立。
 */
class FleetChargePolicyImplTest {

    private FleetChargePolicyImpl fleetChargePolicy;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get(anyString())).thenReturn(null);  // Redis 未命中 → 回退 YAML

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        FleetEnergyProperties properties = new FleetEnergyProperties();
        properties.setLowSocThreshold(20);
        properties.setReturnToChargeThreshold(15);
        properties.setCriticalSocThreshold(5);
        properties.setMinAssignableSoc(30);
        properties.setFullSoc(100);
        properties.setChargeCompleteSoc(90);
        properties.setPluggedStandbyNoDrain(true);

        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(stringRedisTemplate, properties);
        fleetChargePolicy = new FleetChargePolicyImpl(properties, resolver);
    }

    @Test
    void pluggedInFullStandbyShouldSkipDrain() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setBatteryLevel(100);
        FleetRuntime runtime = FleetRuntime.builder()
                .runtimeStage("STANDBY")
                .pluggedIn(true)
                .build();

        assertTrue(fleetChargePolicy.shouldSkipDrain(vehicle, runtime));
        assertTrue(fleetChargePolicy.isAssignable(vehicle));
        assertFalse(fleetChargePolicy.isActivelyCharging("STANDBY"));
    }

    @Test
    void lowSocVehicleShouldNotBeAssignable() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setBatteryLevel(20);

        assertTrue(fleetChargePolicy.isLowSoc(20));
        assertTrue(fleetChargePolicy.shouldReturnToCharge(15));
        assertTrue(fleetChargePolicy.isCriticalSoc(5));
        assertTrue(fleetChargePolicy.isChargeSessionComplete(90));
        assertFalse(fleetChargePolicy.isAssignable(vehicle));
    }

    @Test
    void chargingStageShouldBeDetected() {
        assertTrue(fleetChargePolicy.isActivelyCharging("CHARGING"));
        assertTrue(fleetChargePolicy.isActivelyCharging("TO_CHARGING"));
    }

    /**
     * 阶段八 8.4：验证 Redis 热更新覆盖 YAML 默认值。
     * 当 Redis 中存在有效值时，resolver 应返回 Redis 值而非 YAML 默认值。
     */
    @Test
    void redisHotUpdateShouldOverrideYamlDefault() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        // Redis 将 return-to-charge 阈值改为 25（高于 YAML 的 15）
        // 注意：Mockito 中后注册的 stub 优先，故 anyString() 必须先于具体 key 注册
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.get(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE)).thenReturn("25");

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        FleetEnergyProperties properties = new FleetEnergyProperties();
        properties.setReturnToChargeThreshold(15);  // YAML 默认 15
        properties.setFullSoc(100);

        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(stringRedisTemplate, properties);
        FleetChargePolicyImpl policyWithHotUpdate = new FleetChargePolicyImpl(properties, resolver);

        // SOC 20 高于 YAML 默认 15，但低于 Redis 热更新值 25，应触发回充
        assertTrue(policyWithHotUpdate.shouldReturnToCharge(20),
                "Redis 热更新阈值 25 应覆盖 YAML 默认值 15，使 SOC 20 触发回充");
    }

    /**
     * 阶段八 8.4：验证 Redis 中存在非法值时回退 YAML 默认值。
     */
    @Test
    void invalidRedisValueShouldFallbackToYaml() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        // Redis 中存储非数字字符串
        when(valueOps.get(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE)).thenReturn("abc");
        when(valueOps.get(anyString())).thenReturn(null);

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        FleetEnergyProperties properties = new FleetEnergyProperties();
        properties.setReturnToChargeThreshold(15);
        properties.setFullSoc(100);

        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(stringRedisTemplate, properties);
        FleetChargePolicyImpl policyWithInvalidRedis = new FleetChargePolicyImpl(properties, resolver);

        // 非法值应回退 YAML 15，SOC 15 触发回充
        assertTrue(policyWithInvalidRedis.shouldReturnToCharge(15),
                "非法 Redis 值应回退 YAML 默认值 15");
        assertFalse(policyWithInvalidRedis.shouldReturnToCharge(16),
                "YAML 默认值 15 下 SOC 16 不应触发回充");
    }
}
