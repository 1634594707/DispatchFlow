package com.fsd.admin.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fsd.dispatch.entity.PeakModeStateEntity;
import com.fsd.dispatch.mapper.PeakModeStateMapper;
import com.fsd.dispatch.service.PeakModeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §7.2「高峰模式没有任何时间来源」：两条 cron 全 NULL 时调度器恒不触发，而手工开过的 PEAK
 * 没有任何出口 —— 这里守的就是那条兜底与它的可观测面。
 */
class PeakModeCronSchedulerTest {

    private PeakModeStateMapper mapper;
    private PeakModeService peakModeService;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        mapper = mock(PeakModeStateMapper.class);
        peakModeService = mock(PeakModeService.class);
        registry = new SimpleMeterRegistry();
    }

    private PeakModeCronScheduler scheduler(long maxPeakMinutes) {
        return new PeakModeCronScheduler(mapper, peakModeService, registry, maxPeakMinutes);
    }

    private PeakModeStateEntity state(Long parkId, String mode, String cron, String endCron, LocalDateTime enabledAt) {
        PeakModeStateEntity entity = new PeakModeStateEntity();
        entity.setId(1L);
        entity.setParkId(parkId);
        entity.setMode(mode);
        entity.setTemplateCode("DAILY");
        entity.setScheduleCron(cron);
        entity.setScheduleEndCron(endCron);
        entity.setEnabledAt(enabledAt);
        entity.setUpdatedAt(enabledAt);
        return entity;
    }

    private void given(PeakModeStateEntity... states) {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(states));
    }

    @Test
    @DisplayName("停在 PEAK、没有结束 cron、已超过上限 ⇒ 回落 NORMAL 并计数")
    void peakWithoutEndCronMustExpire() {
        given(state(2L, "PEAK", null, null, LocalDateTime.now().minusHours(3)));

        scheduler(120).applyScheduledPeakModes();

        verify(peakModeService).setMode(eq(2L), eq("NORMAL"), eq("DAILY"), any(), any());
        // 回归：曾经这里用 updateById(本轮快照) 记触发时间，把刚设的 NORMAL 又覆盖回 PEAK
        // —— 日志每轮都报"已回落"，库里永远还是 PEAK。现在只准打时间戳那一列。
        verify(mapper, never()).updateById(any(PeakModeStateEntity.class));
        verify(mapper).update(isNull(), any(UpdateWrapper.class));
        assertEquals(1D, registry.counter(PeakModeCronScheduler.AUTO_RESET_METRIC,
                "park", "2").count(), "兜底动作必须留指标点");
    }

    @Test
    @DisplayName("上限之内不动手：兜底不能把刚开的高峰关掉")
    void recentPeakIsLeftAlone() {
        given(state(2L, "PEAK", null, null, LocalDateTime.now().minusMinutes(5)));

        scheduler(120).applyScheduledPeakModes();

        verify(peakModeService, never()).setMode(anyLong(), any(), any(), any(), any());
        assertEquals(1D, registry.get(PeakModeCronScheduler.SCHEDULE_METRIC)
                .tag("park", "2").tag("state", "peak_without_schedule").counter().count(),
                "没到上限也要看得见\"高峰没有出口\"这件事");
    }

    @Test
    @DisplayName("有结束 cron 时兜底不越权（哪怕已超过上限时长）")
    void endCronOwnsTheExit() {
        given(state(2L, "PEAK", null, "0 0 4 1 1 ?", LocalDateTime.now().minusDays(1)));

        scheduler(120).applyScheduledPeakModes();

        verify(peakModeService, never()).setMode(anyLong(), any(), any(), any(), any());
        assertTrue(registry.find(PeakModeCronScheduler.AUTO_RESET_METRIC).counter() == null,
                "配了结束 cron 就不该由兜底关档");
    }

    @Test
    @DisplayName("两条 cron 全 NULL 且 NORMAL ⇒ 报 no_time_source（高峰从未生效，别静默）")
    void normalWithoutScheduleIsObservable() {
        given(state(1L, "NORMAL", null, null, LocalDateTime.now()));

        scheduler(120).applyScheduledPeakModes();

        verify(peakModeService, never()).setMode(anyLong(), any(), any(), any(), any());
        assertEquals(1D, registry.get(PeakModeCronScheduler.SCHEDULE_METRIC)
                .tag("park", "1").tag("state", "no_time_source").counter().count());
    }

    @Test
    @DisplayName("cron 写错与没配 cron 必须可区分")
    void invalidCronIsNotSilent() {
        given(state(1L, "NORMAL", "not-a-cron", null, LocalDateTime.now()));

        scheduler(120).applyScheduledPeakModes();

        assertEquals(1D, registry.get(PeakModeCronScheduler.SCHEDULE_METRIC)
                .tag("park", "1").tag("state", "invalid_cron").counter().count(),
                "写错的 cron 要单独计一次（没配的那条走空值分支，不算错）");
    }
}
