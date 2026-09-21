package com.fsd.admin.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fsd.dispatch.entity.PeakModeStateEntity;
import com.fsd.dispatch.mapper.PeakModeStateMapper;
import com.fsd.dispatch.service.PeakModeService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PeakModeCronScheduler {

    private static final Logger log = LoggerFactory.getLogger(PeakModeCronScheduler.class);

    /** 没有结束 cron 的 PEAK 超时自动回落 —— 计数是这条兜底唯一对外可见的痕迹。 */
    static final String AUTO_RESET_METRIC = "dispatchflow.peak.auto_reset";

    /** 高峰档有没有时间来源（no_time_source / peak_without_schedule / invalid_cron）。 */
    static final String SCHEDULE_METRIC = "dispatchflow.peak.schedule";

    private final PeakModeStateMapper peakModeStateMapper;
    private final PeakModeService peakModeService;
    private final MeterRegistry registry;
    private final Duration maxPeakDuration;

    public PeakModeCronScheduler(PeakModeStateMapper peakModeStateMapper,
                                 PeakModeService peakModeService,
                                 MeterRegistry registry,
                                 @Value("${fsd.peak-mode.max-peak-duration-minutes:120}") long maxPeakDurationMinutes) {
        this.peakModeStateMapper = peakModeStateMapper;
        this.peakModeService = peakModeService;
        this.registry = registry;
        this.maxPeakDuration = Duration.ofMinutes(Math.max(1L, maxPeakDurationMinutes));
    }

    @Scheduled(fixedDelayString = "${fsd.peak-mode.cron-check-ms:60000}")
    @Transactional
    public void applyScheduledPeakModes() {
        List<PeakModeStateEntity> states = peakModeStateMapper.selectList(new LambdaQueryWrapper<>());
        LocalDateTime now = LocalDateTime.now();
        for (PeakModeStateEntity state : states) {
            if (shouldFire(state.getScheduleCron(), state.getLastSchedulePeakAt(), now, state.getParkId())) {
                peakModeService.setMode(
                        state.getParkId(),
                        "PEAK",
                        state.getTemplateCode(),
                        state.getScheduleCron(),
                        state.getScheduleEndCron());
                stampPeakFired(state.getParkId(), now);
                log.info("Peak mode cron activated parkId={}", state.getParkId());
            }
            if (shouldFire(state.getScheduleEndCron(), state.getLastScheduleEndAt(), now, state.getParkId())) {
                peakModeService.setMode(
                        state.getParkId(),
                        "NORMAL",
                        state.getTemplateCode(),
                        state.getScheduleCron(),
                        state.getScheduleEndCron());
                stampEndFired(state.getParkId(), now);
                log.info("Peak mode cron deactivated parkId={}", state.getParkId());
            }
            // §7.2：两条 cron 都为 NULL 时 shouldFire 恒 false ⇒ 高峰档没有任何时间来源，而 mode 已经是
            // PEAK 的园区（本地演示库 park 2 正是：mode=PEAK、两条 cron 全 NULL）**没有任何出口**，
            // peakSocDamping / peakDistanceFactor 会长期生效却没人知道是谁开的。规则：
            // **没有 schedule_end_cron 的 PEAK 必须有上限时长**，超时回落 NORMAL；有结束 cron 时不越权。
            if (isStuckPeak(state, now)) {
                peakModeService.setMode(
                        state.getParkId(),
                        "NORMAL",
                        state.getTemplateCode(),
                        state.getScheduleCron(),
                        state.getScheduleEndCron());
                stampEndFired(state.getParkId(), now);
                registry.counter(AUTO_RESET_METRIC, "park", String.valueOf(state.getParkId())).increment();
                log.warn("peak mode auto-reset: parkId={} 停在 PEAK 且没有 schedule_end_cron，"
                        + "已超过 {} 分钟，回落 NORMAL（要长期高峰请配 cron）",
                        state.getParkId(), maxPeakDuration.toMinutes());
            } else if (!hasCron(state)) {
                // 没有任何时间来源：要么"高峰从未生效"，要么"高峰下不来"——两种都不该是静默的
                registry.counter(SCHEDULE_METRIC, "park", String.valueOf(state.getParkId()),
                        "state", "PEAK".equalsIgnoreCase(state.getMode())
                                ? "peak_without_schedule" : "no_time_source").increment();
            }
        }
    }

    /**
     * 只写"上次触发时间"这一列。<b>不能</b>用 {@code updateById(state)}：state 是本轮开头读出来的快照，
     * 它的 mode/enabledAt 还是旧值，而 {@code setMode} 刚刚把新值写进库并自己 updateById 过一次 ——
     * 再拿快照覆盖一遍等于把刚设的档口改回去。实测（本机演示库 park 2）：日志连着两分钟报
     * "auto-reset 回落 NORMAL"，库里 mode 始终还是 PEAK —— 三条分支都有这个坑，不止兜底那条。
     */
    private void stampPeakFired(Long parkId, LocalDateTime now) {
        peakModeStateMapper.update(null, new UpdateWrapper<PeakModeStateEntity>()
                .eq("park_id", parkId)
                .set("last_schedule_peak_at", now));
    }

    private void stampEndFired(Long parkId, LocalDateTime now) {
        peakModeStateMapper.update(null, new UpdateWrapper<PeakModeStateEntity>()
                .eq("park_id", parkId)
                .set("last_schedule_end_at", now));
    }
    /** 停在 PEAK、没有结束 cron、且已超过上限时长 —— 只有这一种情况兜底会动手。 */
    private boolean isStuckPeak(PeakModeStateEntity state, LocalDateTime now) {
        if (!"PEAK".equalsIgnoreCase(state.getMode()) || hasText(state.getScheduleEndCron())) {
            return false;
        }
        LocalDateTime since = state.getEnabledAt() != null ? state.getEnabledAt() : state.getUpdatedAt();
        return since != null && since.plus(maxPeakDuration).isBefore(now);
    }

    private boolean hasCron(PeakModeStateEntity state) {
        return hasText(state.getScheduleCron()) || hasText(state.getScheduleEndCron());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean shouldFire(String cronExpression, LocalDateTime lastFiredAt, LocalDateTime now, Long parkId) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            LocalDateTime last = lastFiredAt == null ? now.minusMinutes(2) : lastFiredAt;
            LocalDateTime next = cron.next(last);
            return next != null && !next.isAfter(now);
        } catch (IllegalArgumentException ex) {
            // cron 写错以前是"静默不触发"，和"没配 cron"长得一模一样 ⇒ 必须可区分
            registry.counter(SCHEDULE_METRIC, "park", String.valueOf(parkId), "state", "invalid_cron").increment();
            return false;
        }
    }
}
