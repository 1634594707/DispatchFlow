package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.DispatchStrategyProfileEntity;
import com.fsd.dispatch.mapper.DispatchStrategyProfileMapper;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService.AssignStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 钉住 §7.2 前两项修复的行为契约：一单一次解析、稳定分桶、档案未覆盖字段沿用 YAML。
 */
class DispatchStrategyRuntimeServiceImplTest {

    private DispatchStrategyProfileMapper profileMapper;
    private FleetEnergyProperties yamlEnergy;
    private DispatchScoringProperties yamlScoring;
    private DispatchStrategyRuntimeServiceImpl service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(DispatchStrategyProfileMapper.class);
        yamlEnergy = new FleetEnergyProperties();
        yamlEnergy.setBusyDrainMetersPerPercent(220D);
        yamlEnergy.setChargeRatePerTick(7);
        yamlEnergy.setMinAssignableSoc(40);
        yamlScoring = new DispatchScoringProperties();
        yamlScoring.setWeightDistance(2.5D);
        yamlScoring.setWeightSocMargin(0.4D);
        service = newService();
    }

    private DispatchStrategyRuntimeServiceImpl newService() {
        DispatchStrategyRuntimeServiceImpl impl =
                new DispatchStrategyRuntimeServiceImpl(profileMapper, yamlScoring, yamlEnergy);
        impl.refreshCache();
        return impl;
    }

    private DispatchStrategyProfileEntity profile(String type, Integer grayPercent, Integer minAssignableSoc) {
        DispatchStrategyProfileEntity entity = new DispatchStrategyProfileEntity();
        entity.setId(9L);
        entity.setProfileType(type);
        entity.setActiveFlag(1);
        entity.setGrayPercent(grayPercent);
        entity.setMinAssignableSoc(minAssignableSoc);
        entity.setDeleted(0);
        return entity;
    }

    private void stubProfiles(DispatchStrategyProfileEntity... profiles) {
        when(profileMapper.selectList(any())).thenReturn(List.of(profiles));
        service.refreshCache();
    }

    @Test
    void energyCarriesEveryYamlValueTheProfileDoesNotOverride() {
        stubProfiles(profile("PRODUCTION", null, 25));

        AssignStrategy strategy = service.strategyForAssign(1L, "order:1001");

        // 回归点：修复前 toEnergy 只拷 3 个字段，其余回落到类默认值，
        // 使 FSD_FLEET_ENERGY_BUSY_DRAIN_METERS_PER_PERCENT 在派单链路静默失效。
        assertEquals(220D, strategy.energy().getBusyDrainMetersPerPercent());
        assertEquals(7, strategy.energy().getChargeRatePerTick());
        assertEquals(25, strategy.energy().getMinAssignableSoc());
        assertEquals(40, yamlEnergy.getMinAssignableSoc(), "YAML bean 本身不应被改写");
    }

    @Test
    void scoringCarriesEveryYamlValueTheProfileDoesNotOverride() {
        stubProfiles();

        AssignStrategy strategy = service.strategyForAssign(1L, "order:1001");

        assertEquals(2.5D, strategy.scoring().getWeightDistance());
        assertEquals(0.4D, strategy.scoring().getWeightSocMargin());
        assertTrue(strategy.productionSide());
    }

    @Test
    void sameBucketKeyAlwaysResolvesToTheSameSide() {
        stubProfiles(profile("EXPERIMENT", 50, 25));

        for (int i = 0; i < 300; i++) {
            String key = "order:" + i;
            boolean first = service.strategyForAssign(1L, key).productionSide();
            boolean second = service.strategyForAssign(1L, key).productionSide();
            assertEquals(first, second, "同一单两次解析必须同侧: " + key);
        }
    }

    @Test
    void grayPercentSplitsDistinctOrdersNearTheConfiguredRatio() {
        stubProfiles(profile("EXPERIMENT", 30, 25));

        int experiment = 0;
        int total = 2000;
        for (int i = 0; i < total; i++) {
            if (!service.strategyForAssign(1L, "order:" + i).productionSide()) {
                experiment++;
            }
        }
        double ratio = experiment / (double) total;
        assertTrue(ratio > 0.26 && ratio < 0.34, "灰度比例偏离 30% 太多: " + ratio);
    }

    @Test
    void experimentProfileWithZeroGrayPercentNeverWins() {
        stubProfiles(profile("EXPERIMENT", 0, 25));

        for (int i = 0; i < 50; i++) {
            assertTrue(service.strategyForAssign(1L, "order:" + i).productionSide());
        }
    }

    @Test
    void missingBucketKeyIsDeterministicInsteadOfAReroll() {
        stubProfiles(profile("EXPERIMENT", 50, 25));

        AssignStrategy first = service.strategyForAssign(1L, null);
        AssignStrategy second = service.strategyForAssign(1L, "  ");

        assertEquals(first.productionSide(), second.productionSide());
        assertEquals(0, first.bucket());
        assertNotNull(first.energy());
    }

    @Test
    void energyAndScoringOfOneOrderComeFromTheSameProfile() {
        DispatchStrategyProfileEntity experiment = profile("EXPERIMENT", 100, 25);
        experiment.setWeightDistance(new BigDecimal("9.9"));
        DispatchStrategyProfileEntity production = profile("PRODUCTION", null, 60);
        production.setId(11L);
        production.setWeightDistance(new BigDecimal("1.1"));
        stubProfiles(experiment, production);

        AssignStrategy strategy = service.strategyForAssign(1L, "order:7");

        assertEquals(9.9D, strategy.scoring().getWeightDistance());
        assertEquals(25, strategy.energy().getMinAssignableSoc());
        assertEquals(9L, strategy.profileId());
    }
}
