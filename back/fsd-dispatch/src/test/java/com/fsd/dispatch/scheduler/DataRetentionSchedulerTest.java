package com.fsd.dispatch.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.mapper.DataRetentionMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 路线图 §7.4：四张无界表的保留期清理。
 * 重点是"删得动"（按时间列 + 批次收敛）和"删不坏"（outbox 只碰 PUBLISHED 终态）。
 */
class DataRetentionSchedulerTest {

    private DataRetentionScheduler scheduler(DataRetentionMapper mapper, boolean enabled, int batch) {
        return new DataRetentionScheduler(mapper, enabled, batch, 200, 30, 30, 30, 90);
    }

    @Test
    void everyTableIsPurgedOnceWhenNothingOlderThanRetentionExists() {
        DataRetentionMapper mapper = mock(DataRetentionMapper.class);
        when(mapper.purgeTelemetryBefore(any(), anyInt())).thenReturn(0);
        when(mapper.purgeWebhookLogBefore(any(), anyInt())).thenReturn(0);
        when(mapper.purgePublishedOutboxBefore(any(), anyInt())).thenReturn(0);
        when(mapper.purgeIdempotencyBefore(any(), anyInt())).thenReturn(0);

        scheduler(mapper, true, 500).purgeExpired();

        verify(mapper).purgeTelemetryBefore(any(), eq(500));
        verify(mapper).purgeWebhookLogBefore(any(), eq(500));
        verify(mapper).purgePublishedOutboxBefore(any(), eq(500));
        verify(mapper).purgeIdempotencyBefore(any(), eq(500));
    }

    @Test
    void keepsBatchingUntilAPartialBatchAndStopsAtTheCutoff() {
        DataRetentionMapper mapper = mock(DataRetentionMapper.class);
        // 满批 500 → 再一批 500 → 尾批 120 ⇒ 共 3 批 1120 行后收敛
        when(mapper.purgeTelemetryBefore(any(), eq(500))).thenReturn(500, 500, 120);
        when(mapper.purgeWebhookLogBefore(any(), anyInt())).thenReturn(0);
        when(mapper.purgePublishedOutboxBefore(any(), anyInt())).thenReturn(0);
        when(mapper.purgeIdempotencyBefore(any(), anyInt())).thenReturn(0);

        scheduler(mapper, true, 500).purgeExpired();

        ArgumentCaptor<LocalDateTime> cutoffs = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(3)).purgeTelemetryBefore(cutoffs.capture(), eq(500));
        LocalDateTime first = cutoffs.getAllValues().get(0);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        assertTrue(Math.abs(java.time.Duration.between(first, thirtyDaysAgo).toMinutes()) < 5,
                "cutoff 应当是 now-30d，实测偏移过大: " + first);
    }

    @Test
    void disabledSwitchTouchesNothing() {
        DataRetentionMapper mapper = mock(DataRetentionMapper.class);

        scheduler(mapper, false, 500).purgeExpired();

        verify(mapper, never()).purgeTelemetryBefore(any(), anyInt());
        verify(mapper, never()).purgeIdempotencyBefore(any(), anyInt());
    }

    /**
     * outbox 的删除条件写在 {@code @Delete} 的 SQL 里，mock 永远看不见它 ⇒ 用反射把这条安全约束钉住：
     * 少了 {@code status = 'PUBLISHED'} 就会把待重试/失败事件一起删掉，等于制造永久失踪。
     */
    @Test
    void outboxPurgeSqlMustBeRestrictedToPublishedRowsOnly() throws Exception {
        Delete delete = DataRetentionMapper.class
                .getMethod("purgePublishedOutboxBefore", LocalDateTime.class, int.class)
                .getAnnotation(Delete.class);
        String sql = String.join(" ", delete.value());
        assertTrue(sql.contains("status = 'PUBLISHED'"), "outbox 清理必须只碰终态: " + sql);
        assertTrue(sql.contains("created_at"), "必须按时间列走索引: " + sql);
        assertEquals(-1, sql.indexOf("DELETE FROM t_dispatch_event_outbox WHERE created_at"),
                "不允许出现不带状态条件的 outbox 删除");
    }
}
