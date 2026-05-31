package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.PeakModeStateEntity;

public interface PeakModeService {

    PeakModeStateEntity getState(Long parkId);

    boolean isPeakMode(Long parkId);

    PeakModeStateEntity setMode(Long parkId, String mode, String templateCode, String scheduleCron, String scheduleEndCron);
}
