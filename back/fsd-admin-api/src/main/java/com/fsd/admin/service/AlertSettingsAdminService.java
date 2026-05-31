package com.fsd.admin.service;

import com.fsd.admin.dto.AdminAlertSettingsUpsertRequest;
import com.fsd.admin.vo.AdminAlertSettingsResponse;

public interface AlertSettingsAdminService {

    AdminAlertSettingsResponse getSettings(Long userId);

    AdminAlertSettingsResponse saveSettings(Long userId, AdminAlertSettingsUpsertRequest request);
}
