package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminAlertSettingsUpsertRequest;
import com.fsd.admin.service.AlertSettingsAdminService;
import com.fsd.admin.vo.AdminAlertSettingsResponse;
import com.fsd.dispatch.entity.UserAlertSettingEntity;
import com.fsd.dispatch.mapper.UserAlertSettingMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertSettingsAdminServiceImpl implements AlertSettingsAdminService {

    private static final String DEFAULT_RULES = "{\"soundEnabled\":true,\"browserNotifyEnabled\":true,\"severityFilter\":[\"HIGH\",\"MEDIUM\"]}";

    private final UserAlertSettingMapper userAlertSettingMapper;

    public AlertSettingsAdminServiceImpl(UserAlertSettingMapper userAlertSettingMapper) {
        this.userAlertSettingMapper = userAlertSettingMapper;
    }

    @Override
    public AdminAlertSettingsResponse getSettings(Long userId) {
        UserAlertSettingEntity entity = userAlertSettingMapper.selectOne(
                new LambdaQueryWrapper<UserAlertSettingEntity>().eq(UserAlertSettingEntity::getUserId, userId));
        String rules = entity == null ? DEFAULT_RULES : entity.getRulesJson();
        return AdminAlertSettingsResponse.builder().rulesJson(rules).build();
    }

    @Override
    @Transactional
    public AdminAlertSettingsResponse saveSettings(Long userId, AdminAlertSettingsUpsertRequest request) {
        UserAlertSettingEntity entity = userAlertSettingMapper.selectOne(
                new LambdaQueryWrapper<UserAlertSettingEntity>().eq(UserAlertSettingEntity::getUserId, userId));
        if (entity == null) {
            entity = new UserAlertSettingEntity();
            entity.setUserId(userId);
            entity.setRulesJson(request.getRulesJson());
            entity.setUpdatedAt(LocalDateTime.now());
            userAlertSettingMapper.insert(entity);
        } else {
            entity.setRulesJson(request.getRulesJson());
            entity.setUpdatedAt(LocalDateTime.now());
            userAlertSettingMapper.updateById(entity);
        }
        return AdminAlertSettingsResponse.builder().rulesJson(request.getRulesJson()).build();
    }
}
