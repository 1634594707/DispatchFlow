package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.PeakModeStateEntity;
import com.fsd.dispatch.mapper.PeakModeStateMapper;
import com.fsd.dispatch.service.PeakModeService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeakModeServiceImpl implements PeakModeService {

    private final PeakModeStateMapper peakModeStateMapper;

    public PeakModeServiceImpl(PeakModeStateMapper peakModeStateMapper) {
        this.peakModeStateMapper = peakModeStateMapper;
    }

    @Override
    public PeakModeStateEntity getState(Long parkId) {
        PeakModeStateEntity state = peakModeStateMapper.selectOne(new LambdaQueryWrapper<PeakModeStateEntity>()
                .eq(PeakModeStateEntity::getParkId, parkId));
        if (state != null) {
            return state;
        }
        PeakModeStateEntity created = new PeakModeStateEntity();
        created.setParkId(parkId);
        created.setMode("NORMAL");
        created.setTemplateCode("DAILY");
        created.setUpdatedAt(LocalDateTime.now());
        peakModeStateMapper.insert(created);
        return created;
    }

    @Override
    public boolean isPeakMode(Long parkId) {
        if (parkId == null) {
            return false;
        }
        PeakModeStateEntity state = getState(parkId);
        return "PEAK".equalsIgnoreCase(state.getMode());
    }

    @Override
    @Transactional
    public PeakModeStateEntity setMode(Long parkId, String mode, String templateCode,
                                       String scheduleCron, String scheduleEndCron) {
        PeakModeStateEntity state = getState(parkId);
        state.setMode(mode == null || mode.isBlank() ? "NORMAL" : mode.toUpperCase());
        if (templateCode != null && !templateCode.isBlank()) {
            state.setTemplateCode(templateCode);
        }
        if (scheduleCron != null) {
            state.setScheduleCron(scheduleCron.isBlank() ? null : scheduleCron);
        }
        if (scheduleEndCron != null) {
            state.setScheduleEndCron(scheduleEndCron.isBlank() ? null : scheduleEndCron);
        }
        state.setEnabledAt(LocalDateTime.now());
        state.setUpdatedAt(LocalDateTime.now());
        if (state.getId() == null) {
            peakModeStateMapper.insert(state);
        } else {
            peakModeStateMapper.updateById(state);
        }
        return state;
    }
}
