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
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class DispatchStrategyRuntimeServiceImpl implements DispatchStrategyRuntimeService {

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
    public DispatchScoringProperties scoringForAssign(Long parkId) {
        return toScoring(resolveForAssign(parkId));
    }

    @Override
    public FleetEnergyProperties energyForAssign(Long parkId) {
        return toEnergy(resolveForAssign(parkId));
    }

    private DispatchStrategyProfileEntity resolveForAssign(Long parkId) {
        DispatchStrategyProfileEntity experiment = pickActive("EXPERIMENT", parkId);
        if (experiment != null && experiment.getGrayPercent() != null && experiment.getGrayPercent() > 0) {
            if (ThreadLocalRandom.current().nextInt(100) < experiment.getGrayPercent()) {
                return experiment;
            }
        }
        DispatchStrategyProfileEntity production = pickActive("PRODUCTION", parkId);
        return production;
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
        DispatchScoringProperties props = new DispatchScoringProperties();
        if (profile == null) {
            props.setWeightDistance(yamlScoring.getWeightDistance());
            props.setWeightSocMargin(yamlScoring.getWeightSocMargin());
            props.setWeightPluggedStandbyBonus(yamlScoring.getWeightPluggedStandbyBonus());
            return props;
        }
        props.setWeightDistance(toDouble(profile.getWeightDistance(), yamlScoring.getWeightDistance()));
        props.setWeightSocMargin(toDouble(profile.getWeightSocMargin(), yamlScoring.getWeightSocMargin()));
        props.setWeightPluggedStandbyBonus(
                toDouble(profile.getWeightPluggedStandbyBonus(), yamlScoring.getWeightPluggedStandbyBonus()));
        return props;
    }

    private FleetEnergyProperties toEnergy(DispatchStrategyProfileEntity profile) {
        FleetEnergyProperties props = new FleetEnergyProperties();
        if (profile == null) {
            props.setMinAssignableSoc(yamlEnergy.getMinAssignableSoc());
            props.setFullSoc(yamlEnergy.getFullSoc());
            return props;
        }
        props.setMinAssignableSoc(profile.getMinAssignableSoc() != null
                ? profile.getMinAssignableSoc() : yamlEnergy.getMinAssignableSoc());
        props.setFullSoc(profile.getFullSoc() != null ? profile.getFullSoc() : yamlEnergy.getFullSoc());
        props.setEnergyRecoveryMode(profile.getEnergyRecoveryMode() != null && !profile.getEnergyRecoveryMode().isBlank()
                ? profile.getEnergyRecoveryMode() : yamlEnergy.getEnergyRecoveryMode());
        return props;
    }

    private double toDouble(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }
}
