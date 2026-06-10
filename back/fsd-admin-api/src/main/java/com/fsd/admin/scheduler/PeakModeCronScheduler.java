package com.fsd.admin.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.PeakModeStateEntity;
import com.fsd.dispatch.mapper.PeakModeStateMapper;
import com.fsd.dispatch.service.PeakModeService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PeakModeCronScheduler {

    private static final Logger log = LoggerFactory.getLogger(PeakModeCronScheduler.class);

    private final PeakModeStateMapper peakModeStateMapper;
    private final PeakModeService peakModeService;

    public PeakModeCronScheduler(PeakModeStateMapper peakModeStateMapper, PeakModeService peakModeService) {
        this.peakModeStateMapper = peakModeStateMapper;
        this.peakModeService = peakModeService;
    }

    @Scheduled(fixedDelayString = "${fsd.peak-mode.cron-check-ms:60000}")
    @Transactional
    public void applyScheduledPeakModes() {
        List<PeakModeStateEntity> states = peakModeStateMapper.selectList(new LambdaQueryWrapper<>());
        LocalDateTime now = LocalDateTime.now();
        for (PeakModeStateEntity state : states) {
            if (shouldFire(state.getScheduleCron(), state.getLastSchedulePeakAt(), now)) {
                peakModeService.setMode(
                        state.getParkId(),
                        "PEAK",
                        state.getTemplateCode(),
                        state.getScheduleCron(),
                        state.getScheduleEndCron());
                state.setLastSchedulePeakAt(now);
                state.setUpdatedAt(now);
                peakModeStateMapper.updateById(state);
                log.info("Peak mode cron activated parkId={}", state.getParkId());
            }
            if (shouldFire(state.getScheduleEndCron(), state.getLastScheduleEndAt(), now)) {
                peakModeService.setMode(
                        state.getParkId(),
                        "NORMAL",
                        state.getTemplateCode(),
                        state.getScheduleCron(),
                        state.getScheduleEndCron());
                state.setLastScheduleEndAt(now);
                state.setUpdatedAt(now);
                peakModeStateMapper.updateById(state);
                log.info("Peak mode cron deactivated parkId={}", state.getParkId());
            }
        }
    }

    private boolean shouldFire(String cronExpression, LocalDateTime lastFiredAt, LocalDateTime now) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            LocalDateTime last = lastFiredAt == null ? now.minusMinutes(2) : lastFiredAt;
            LocalDateTime next = cron.next(last);
            return next != null && !next.isAfter(now);
        } catch (Exception ex) {
            return false;
        }
    }
}
