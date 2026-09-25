package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchPauseStateEntity;
import com.fsd.dispatch.mapper.DispatchPauseStateMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 路线图 §7.4「两处暂停开关合一」：MySQL 是真相、Redis 只是带 TTL 的可重建缓存。
 *
 * <p>改前这里是纯 Redis 且 {@code set(key,"1")} 不带过期、真相表零读者 —— 下面每条断言都对着那两个毛病之一。</p>
 */
@ExtendWith(MockitoExtension.class)
class DispatchPauseControlServiceImplTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private DispatchPauseStateMapper pauseStateMapper;

    private DispatchPauseControlServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new DispatchPauseControlServiceImpl(redis, pauseStateMapper, 60L);
    }

    private DispatchPauseStateEntity row(long parkId, int paused) {
        DispatchPauseStateEntity entity = new DispatchPauseStateEntity();
        entity.setParkId(parkId);
        entity.setIsPaused(paused);
        return entity;
    }

    @Test
    void cacheMissReadsMysqlAndBackfillsWithTtl() {
        when(valueOps.get(any(String.class))).thenReturn(null);
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(row(0L, 0), row(7L, 1));

        assertTrue(service.isDispatchPaused(7L));

        verify(valueOps).set(eq("fsd:dispatch:pause:global"), eq("0"), eq(Duration.ofSeconds(60)));
        verify(valueOps).set(eq("fsd:dispatch:pause:park:7"), eq("1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void cacheHitMustNotTouchDatabase() {
        when(valueOps.get("fsd:dispatch:pause:global")).thenReturn("1");

        assertTrue(service.isDispatchPaused(7L));

        verify(pauseStateMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(valueOps, never()).set(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void parkResumeOnlyUnpausesThatParkWhileGlobalStaysPaused() {
        when(valueOps.get(any(String.class))).thenReturn(null);
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(row(0L, 1));

        assertTrue(service.isDispatchPaused(7L), "全局档暂停即一切暂停，不必再查单园");
        verify(pauseStateMapper, never()).updateById(any(DispatchPauseStateEntity.class));
    }

    @Test
    void writeGoesToMysqlAndEvictsCacheInsteadOfWritingRedis() {
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.setDispatchPaused(7L, true, "暴雨封路", "admin-zhang");

        ArgumentCaptor<DispatchPauseStateEntity> inserted = ArgumentCaptor.forClass(DispatchPauseStateEntity.class);
        verify(pauseStateMapper).insert(inserted.capture());
        assertEquals(7L, inserted.getValue().getParkId());
        assertEquals(Integer.valueOf(1), inserted.getValue().getIsPaused());
        assertEquals("暴雨封路", inserted.getValue().getPauseReason());
        assertEquals("admin-zhang", inserted.getValue().getPausedBy(), "审计列必须是服务端给的会话身份");
        org.junit.jupiter.api.Assertions.assertNotNull(inserted.getValue().getPausedAt());
        verify(redis).delete("fsd:dispatch:pause:park:7");
        // Redis 不再是存储：写路径一个字都不许往里塞
        verify(valueOps, never()).set(any(String.class), any(String.class));
        verify(valueOps, never()).set(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void globalSwitchUsesTheParkIdZeroRow() {
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.setDispatchPaused(null, true, "全网演练", "admin-li");

        ArgumentCaptor<DispatchPauseStateEntity> inserted = ArgumentCaptor.forClass(DispatchPauseStateEntity.class);
        verify(pauseStateMapper).insert(inserted.capture());
        assertEquals(0L, inserted.getValue().getParkId(), "全局档必须落在 park_id=0 这一行（V58）");
        verify(redis).delete("fsd:dispatch:pause:global");
    }

    /** 没有原因的紧急停止事后无法复盘，服务端直接拒，不接受"先按下再说"。 */
    @Test
    void pausingWithoutReasonIsRejected() {
        com.fsd.common.exception.BusinessException ex = assertThrows(
                com.fsd.common.exception.BusinessException.class,
                () -> service.setDispatchPaused(7L, true, "   ", "admin-zhang"));

        assertEquals("DISPATCH_PAUSE_REASON_REQUIRED", ex.getCode());
        verify(pauseStateMapper, never()).insert(any(DispatchPauseStateEntity.class));
        verify(pauseStateMapper, never()).updateById(any(DispatchPauseStateEntity.class));
    }

    /** 恢复不带原因时保留上一条：这一行还要解释得清刚结束的那段暂停。 */
    @Test
    void resumeWithoutReasonKeepsThePauseReasonButRecordsWhoResumed() {
        DispatchPauseStateEntity existing = row(7L, 1);
        existing.setId(42L);
        existing.setPauseReason("暴雨封路");
        existing.setPausedBy("admin-zhang");
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.setDispatchPaused(7L, false, null, "admin-wang");

        ArgumentCaptor<DispatchPauseStateEntity> updated = ArgumentCaptor.forClass(DispatchPauseStateEntity.class);
        verify(pauseStateMapper).updateById(updated.capture());
        assertEquals("暴雨封路", updated.getValue().getPauseReason());
        assertEquals("admin-wang", updated.getValue().getPausedBy());
    }

    /** 查看生效中的暂停是谁、因为什么：必须读表，缓存里只有布尔值带不出这些。 */
    @Test
    void pauseStateReadsTheAuditColumnsFromMysqlNotFromCache() {
        DispatchPauseStateEntity global = row(0L, 1);
        global.setPauseReason("暴雨封路");
        global.setPausedBy("admin-zhang");
        global.setPausedAt(java.time.LocalDateTime.of(2026, 9, 22, 14, 2));
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(global);

        com.fsd.dispatch.service.DispatchPauseControlService.PauseState state = service.pauseState(7L);

        assertTrue(state.paused());
        assertEquals("暴雨封路", state.reason());
        assertEquals("admin-zhang", state.pausedBy());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 22, 14, 2), state.pausedAt());
        verify(valueOps, never()).get(any(String.class));
    }

    @Test
    void pauseStateIsQuietWhenNoRowExists() {
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        com.fsd.dispatch.service.DispatchPauseControlService.PauseState state = service.pauseState(7L);

        assertFalse(state.paused());
        assertNull(state.reason());
    }

    @Test
    void resumeUpdatesExistingRowInsteadOfInserting() {
        DispatchPauseStateEntity existing = row(7L, 1);
        existing.setId(42L);
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.setDispatchPaused(7L, false, "雨停复通", "admin-wang");

        ArgumentCaptor<DispatchPauseStateEntity> updated = ArgumentCaptor.forClass(DispatchPauseStateEntity.class);
        verify(pauseStateMapper).updateById(updated.capture());
        assertEquals(Integer.valueOf(0), updated.getValue().getIsPaused());
        assertEquals("雨停复通", updated.getValue().getPauseReason());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getValue().getResumedAt());
        verify(pauseStateMapper, never()).insert(any(DispatchPauseStateEntity.class));
    }

    @Test
    void missingRowMeansNotPaused() {
        when(valueOps.get(any(String.class))).thenReturn(null);
        when(pauseStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertFalse(service.isDispatchPaused(9L));
        assertFalse(service.isGlobalDispatchPaused());
    }
}
