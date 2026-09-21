package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.DispatchStrategyProfileEntity;
import com.fsd.dispatch.mapper.DispatchStrategyProfileMapper;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class DispatchStrategyRuntimeServiceImpl implements DispatchStrategyRuntimeService {

    private static final int BUCKET_COUNT = 100;

    private final DispatchStrategyProfileMapper profileMapper;
    private final DispatchScoringProperties yamlScoring;
    private final FleetEnergyProperties yamlEnergy;

    private volatile List<DispatchStrategyProfileEntity> cachedProfiles = List.of();

    public DispatchStrategyRuntimeServiceImpl(DispatchStrategyProfileMapper profileMapper,
                                              DispatchScoringProperties yamlScoring,
                                              FleetEnergyProperties yamlEnergy) {
        this.profileMapper = profileMapper;
        this.yamlScoring = yamlScoring;
        this.yamlEnergy = yamlEnergy;
    }

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Override
    public void refreshCache() {
        try {
            cachedProfiles = profileMapper.selectList(new LambdaQueryWrapper<DispatchStrategyProfileEntity>()
                    .eq(DispatchStrategyProfileEntity::getDeleted, 0));
        } catch (RuntimeException ex) {
            cachedProfiles = List.of();
        }
    }

    @Override
    public AssignStrategy strategyForAssign(Long parkId, String bucketKey) {
        int bucket = bucketOf(bucketKey);
        DispatchStrategyProfileEntity experiment = pickActive("EXPERIMENT", parkId);
        if (experiment != null
                && experiment.getGrayPercent() != null && experiment.getGrayPercent() > 0
                && bucket < experiment.getGrayPercent()) {
            return new AssignStrategy(toEnergy(experiment), toScoring(experiment),
                    experiment.getId(), experiment.getProfileType(), experiment.getGrayPercent(), bucket, false);
        }
        DispatchStrategyProfileEntity production = pickActive("PRODUCTION", parkId);
        if (production == null) {
            return new AssignStrategy(toEnergy(null), toScoring(null),
                    null, experiment != null ? "EXPERIMENT" : null,
                    experiment != null ? experiment.getGrayPercent() : null, bucket, true);
        }
        return new AssignStrategy(toEnergy(production), toScoring(production),
                production.getId(), production.getProfileType(), production.getGrayPercent(), bucket, true);
    }

    /**
     * 分桶键用稳定哈希而非随机数：同一单重放必须落进同一侧，否则影子对照与决策快照无法复现。
     * {@code bucketKey} 缺失时固定落 0 号桶（即偏向实验侧），而不是每次重新掷随机数。
     */
    static int bucketOf(String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return 0;
        }
        return Math.floorMod(bucketKey.hashCode(), BUCKET_COUNT);
    }

    private DispatchStrategyProfileEntity pickActive(String type, Long parkId) {
        DispatchStrategyProfileEntity parkMatch = null;
        DispatchStrategyProfileEntity globalMatch = null;
        for (DispatchStrategyProfileEntity profile : cachedProfiles) {
            if (!type.equalsIgnoreCase(profile.getProfileType())) {
                continue;
            }
            if (profile.getActiveFlag() == null || profile.getActiveFlag() != 1) {
                continue;
            }
            if (parkId != null && parkId.equals(profile.getParkId())) {
                parkMatch = profile;
            } else if (profile.getParkId() == null) {
                globalMatch = profile;
            }
        }
        return parkMatch != null ? parkMatch : globalMatch;
    }

    private DispatchScoringProperties toScoring(DispatchStrategyProfileEntity profile) {
        // 从 YAML bean 复制全部字段：档案未覆盖的项必须沿用 application.yml 的值，
        // 不能回落到类默认值（那会让 FSD_DISPATCH_SCORING_* 配置静默失效）。
        DispatchScoringProperties props = new DispatchScoringProperties();
        BeanUtils.copyProperties(yamlScoring, props);
        if (profile == null) {
            return props;
        }
        props.setWeightDistance(toDouble(profile.getWeightDistance(), props.getWeightDistance()));
        props.setWeightSocMargin(toDouble(profile.getWeightSocMargin(), props.getWeightSocMargin()));
        props.setWeightPluggedStandbyBonus(
                toDouble(profile.getWeightPluggedStandbyBonus(), props.getWeightPluggedStandbyBonus()));
        return props;
    }

    private FleetEnergyProperties toEnergy(DispatchStrategyProfileEntity profile) {
        // 同上：BeanUtils 保证 busyDrainMetersPerPercent / chargeRatePerTick 等未进档案表的
        // 字段沿用 YAML 值。此前只拷 3 个字段，使 FSD_FLEET_ENERGY_BUSY_DRAIN_METERS_PER_PERCENT
        // 在整条派单链路上失效，SOC 校验恒按 150 m/1% 计算。
        FleetEnergyProperties props = new FleetEnergyProperties();
        BeanUtils.copyProperties(yamlEnergy, props);
        if (profile == null) {
            return props;
        }
        props.setMinAssignableSoc(profile.getMinAssignableSoc() != null
                ? profile.getMinAssignableSoc() : props.getMinAssignableSoc());
        props.setFullSoc(profile.getFullSoc() != null ? profile.getFullSoc() : props.getFullSoc());
        props.setEnergyRecoveryMode(profile.getEnergyRecoveryMode() != null && !profile.getEnergyRecoveryMode().isBlank()
                ? profile.getEnergyRecoveryMode() : props.getEnergyRecoveryMode());
        return props;
    }

    private double toDouble(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }
}
