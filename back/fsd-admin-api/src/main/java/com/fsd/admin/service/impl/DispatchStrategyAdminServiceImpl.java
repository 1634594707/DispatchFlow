package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminDispatchStrategyUpsertRequest;
import com.fsd.admin.service.DispatchStrategyAdminService;
import com.fsd.admin.vo.AdminDispatchStrategyResponse;
import com.fsd.admin.vo.AdminStrategyChangeLogResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchStrategyChangeLogEntity;
import com.fsd.dispatch.entity.DispatchStrategyProfileEntity;
import com.fsd.dispatch.mapper.DispatchStrategyChangeLogMapper;
import com.fsd.dispatch.mapper.DispatchStrategyProfileMapper;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchStrategyAdminServiceImpl implements DispatchStrategyAdminService {

    private final DispatchStrategyProfileMapper profileMapper;
    private final DispatchStrategyChangeLogMapper changeLogMapper;
    private final DispatchStrategyRuntimeService runtimeService;

    public DispatchStrategyAdminServiceImpl(DispatchStrategyProfileMapper profileMapper,
                                              DispatchStrategyChangeLogMapper changeLogMapper,
                                              DispatchStrategyRuntimeService runtimeService) {
        this.profileMapper = profileMapper;
        this.changeLogMapper = changeLogMapper;
        this.runtimeService = runtimeService;
    }

    @Override
    public List<AdminDispatchStrategyResponse> listProfiles() {
        return profileMapper.selectList(new LambdaQueryWrapper<DispatchStrategyProfileEntity>()
                        .eq(DispatchStrategyProfileEntity::getDeleted, 0)
                        .orderByDesc(DispatchStrategyProfileEntity::getActiveFlag)
                        .orderByAsc(DispatchStrategyProfileEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<AdminStrategyChangeLogResponse> listChangeLogs() {
        return changeLogMapper.selectList(new LambdaQueryWrapper<DispatchStrategyChangeLogEntity>()
                        .orderByDesc(DispatchStrategyChangeLogEntity::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(log -> AdminStrategyChangeLogResponse.builder()
                        .id(log.getId())
                        .profileId(log.getProfileId())
                        .profileName(log.getProfileName())
                        .changeType(log.getChangeType())
                        .operatorName(log.getOperatorName())
                        .changeSummary(log.getChangeSummary())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public AdminDispatchStrategyResponse create(AdminDispatchStrategyUpsertRequest request, String operatorName) {
        DispatchStrategyProfileEntity entity = new DispatchStrategyProfileEntity();
        apply(entity, request);
        entity.setActiveFlag(0);
        entity.setDeleted(0);
        entity.setVersion(0);
        profileMapper.insert(entity);
        appendLog(entity, "CREATE", operatorName, "新建策略配置");
        runtimeService.refreshCache();
        return toResponse(entity);
    }

    @Override
    @Transactional
    public AdminDispatchStrategyResponse update(Long id, AdminDispatchStrategyUpsertRequest request, String operatorName) {
        DispatchStrategyProfileEntity entity = require(id);
        apply(entity, request);
        profileMapper.updateById(entity);
        appendLog(entity, "UPDATE", operatorName, "更新策略参数");
        runtimeService.refreshCache();
        return toResponse(entity);
    }

    @Override
    @Transactional
    public void activate(Long id, String operatorName) {
        DispatchStrategyProfileEntity target = require(id);
        List<DispatchStrategyProfileEntity> sameType = profileMapper.selectList(
                new LambdaQueryWrapper<DispatchStrategyProfileEntity>()
                        .eq(DispatchStrategyProfileEntity::getDeleted, 0)
                        .eq(DispatchStrategyProfileEntity::getProfileType, target.getProfileType()));
        for (DispatchStrategyProfileEntity profile : sameType) {
            if (profile.getParkId() == null && target.getParkId() == null
                    || profile.getParkId() != null && profile.getParkId().equals(target.getParkId())) {
                profile.setActiveFlag(profile.getId().equals(id) ? 1 : 0);
                profileMapper.updateById(profile);
            }
        }
        appendLog(target, "ACTIVATE", operatorName, "激活策略 " + target.getProfileName());
        runtimeService.refreshCache();
    }

    private void apply(DispatchStrategyProfileEntity entity, AdminDispatchStrategyUpsertRequest request) {
        entity.setProfileName(request.getProfileName());
        entity.setProfileType(request.getProfileType());
        entity.setGrayPercent(request.getGrayPercent() == null ? 0 : request.getGrayPercent());
        entity.setParkId(request.getParkId());
        entity.setWeightDistance(request.getWeightDistance());
        entity.setWeightSocMargin(request.getWeightSocMargin());
        entity.setWeightPluggedStandbyBonus(request.getWeightPluggedStandbyBonus());
        entity.setMinAssignableSoc(request.getMinAssignableSoc());
        entity.setFullSoc(request.getFullSoc());
        entity.setRemark(request.getRemark());
    }

    private void appendLog(DispatchStrategyProfileEntity entity, String type, String operator, String summary) {
        DispatchStrategyChangeLogEntity log = new DispatchStrategyChangeLogEntity();
        log.setProfileId(entity.getId());
        log.setProfileName(entity.getProfileName());
        log.setChangeType(type);
        log.setOperatorName(operator);
        log.setChangeSummary(summary);
        changeLogMapper.insert(log);
    }

    private DispatchStrategyProfileEntity require(Long id) {
        DispatchStrategyProfileEntity entity = profileMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() != 0) {
            throw new BusinessException("STRATEGY_NOT_FOUND", "策略不存在");
        }
        return entity;
    }

    private AdminDispatchStrategyResponse toResponse(DispatchStrategyProfileEntity entity) {
        return AdminDispatchStrategyResponse.builder()
                .id(entity.getId())
                .profileName(entity.getProfileName())
                .profileType(entity.getProfileType())
                .active(entity.getActiveFlag() != null && entity.getActiveFlag() == 1)
                .grayPercent(entity.getGrayPercent())
                .parkId(entity.getParkId())
                .weightDistance(entity.getWeightDistance())
                .weightSocMargin(entity.getWeightSocMargin())
                .weightPluggedStandbyBonus(entity.getWeightPluggedStandbyBonus())
                .minAssignableSoc(entity.getMinAssignableSoc())
                .fullSoc(entity.getFullSoc())
                .remark(entity.getRemark())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
