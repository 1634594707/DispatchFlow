package com.fsd.dispatch.fleet.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.FleetEnergyProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P1-2 阈值出口的可观测面：null Redis 模板回退 YAML 并如实记来源；
 * describe() 覆盖全部五档；值变化后来源记录随动。
 */
class FleetEnergyThresholdResolverTest {

    @Test
    @DisplayName("无 Redis：五档全走 YAML，来源记录如实标注")
    void yamlFallbackRecordedAsYamlSource() {
        FleetEnergyProperties properties = new FleetEnergyProperties();
        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(null, properties);

        assertEquals(properties.getReturnToChargeThreshold(), resolver.getReturnToChargeThreshold());
        assertEquals(properties.getLowSocThreshold(), resolver.getLowSocThreshold());

        List<FleetEnergyThresholdResolver.ThresholdSource> sources = resolver.describe();
        assertEquals(5, sources.size(), "P1-2 起五档阈值（含 low-soc）都要有来源记录");
        assertTrue(sources.stream().allMatch(source -> "YAML".equals(source.source())),
                "无 Redis 时来源必须记 YAML，不能谎称 REDIS");
        assertTrue(sources.stream().allMatch(source -> source.resolvedAt() != null));
    }

    @Test
    @DisplayName("Redis 覆盖：合法值记 REDIS 来源，非法值回退 YAML")
    void redisOverrideRecordedAsRedisSource() {
        FleetEnergyProperties properties = new FleetEnergyProperties();
        org.springframework.data.redis.core.StringRedisTemplate template =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(template.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.when(ops.get(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE))
                .thenReturn("25");
        org.mockito.Mockito.when(ops.get(FleetEnergyThresholdResolver.KEY_LOW_SOC))
                .thenReturn("not-a-number");

        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(template, properties);
        assertEquals(25, resolver.getReturnToChargeThreshold(), "合法覆盖值必须生效");
        assertEquals(properties.getLowSocThreshold(), resolver.getLowSocThreshold(),
                "非法值必须回退 YAML");

        List<FleetEnergyThresholdResolver.ThresholdSource> sources = resolver.describe();
        assertEquals("REDIS", sources.stream()
                .filter(s -> s.key().equals(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE))
                .findFirst().orElseThrow().source());
        assertEquals("YAML", sources.stream()
                .filter(s -> s.key().equals(FleetEnergyThresholdResolver.KEY_LOW_SOC))
                .findFirst().orElseThrow().source());
    }

    @Test
    @DisplayName("值变化时来源记录随动（热更新可观测）")
    void valueChangeUpdatesSourceRecord() {
        FleetEnergyProperties properties = new FleetEnergyProperties();
        org.springframework.data.redis.core.StringRedisTemplate template =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(template.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.when(ops.get(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE))
                .thenReturn("25");

        FleetEnergyThresholdResolver resolver = new FleetEnergyThresholdResolver(template, properties);
        assertEquals(25, resolver.getReturnToChargeThreshold());
        org.mockito.Mockito.when(ops.get(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE))
                .thenReturn("35");
        resolver.refresh();
        assertEquals(35, resolver.getReturnToChargeThreshold(), "refresh 后新值必须生效");
        assertEquals(35, resolver.describe().stream()
                .filter(s -> s.key().equals(FleetEnergyThresholdResolver.KEY_RETURN_TO_CHARGE))
                .findFirst().orElseThrow().value());
    }
}
