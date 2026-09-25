package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchPauseStateEntity;
import com.fsd.dispatch.mapper.DispatchPauseStateMapper;
import com.fsd.dispatch.service.DispatchPauseControlService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 暂停派单开关（路线图 §7.4 状态与消息边界）。
 *
 * <p><b>MySQL = 真相，Redis = 可重建缓存。</b>此前这里只写 Redis 且不带 TTL：Redis 被 flush 或换实例后
 * 暂停状态静默消失（紧急停止自己失效），而 V43 建的真相表 {@code t_dispatch_pause_state} 零读者。
 * 现在读走缓存穿透到表，写先落表再逐出缓存键 ⇒ 缓存丢了能从表重建，表不会从缓存丢失。</p>
 *
 * <p>全局档用 {@code park_id = 0} 这一行表示（V58）：既保住 {@code park_id NOT NULL} 与
 * {@code uk_park} 唯一键，又让"全园暂停"和"单园暂停"落在同一张表、同一条读路径上。
 * 缓存键沿用原有名字（{@code fsd:dispatch:pause:global} / {@code :park:{id}}），运维手感不变。</p>
 */
@Service
public class DispatchPauseControlServiceImpl implements DispatchPauseControlService {

    private static final long GLOBAL_PARK_ID = 0L;
    private static final String KEY_GLOBAL = "fsd:dispatch:pause:global";
    private static final String KEY_PARK_PREFIX = "fsd:dispatch:pause:park:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DispatchPauseStateMapper pauseStateMapper;
    private final Duration cacheTtl;

    public DispatchPauseControlServiceImpl(StringRedisTemplate stringRedisTemplate,
                                           DispatchPauseStateMapper pauseStateMapper,
                                           @Value("${fsd.dispatch.pause-cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.pauseStateMapper = pauseStateMapper;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Override
    public boolean isDispatchPaused(Long parkId) {
        return isPausedScoped(KEY_GLOBAL, GLOBAL_PARK_ID)
                || (parkId != null && isPausedScoped(KEY_PARK_PREFIX + parkId, parkId));
    }

    @Override
    public void setDispatchPaused(Long parkId, boolean paused, String reason, String operator) {
        String trimmedReason = reason == null ? null : reason.trim();
        if (paused && (trimmedReason == null || trimmedReason.isEmpty())) {
            throw new BusinessException("DISPATCH_PAUSE_REASON_REQUIRED", "暂停派单必须填写原因");
        }
        long rowParkId = parkId == null ? GLOBAL_PARK_ID : parkId;
        String cacheKey = parkId == null ? KEY_GLOBAL : KEY_PARK_PREFIX + parkId;
        persist(rowParkId, paused, trimmedReason, operator);
        stringRedisTemplate.delete(cacheKey);
    }

    @Override
    public boolean isGlobalDispatchPaused() {
        return isPausedScoped(KEY_GLOBAL, GLOBAL_PARK_ID);
    }

    /**
     * 直接读表，不走 {@code fsd:dispatch:pause:*} 缓存：缓存里只有布尔值，带不出原因和操作人。
     * 这是管理端一次性的查看，不是派单热路径，多一次主键查询换审计可读性是划算的。
     */
    @Override
    public PauseState pauseState(Long parkId) {
        DispatchPauseStateEntity global = readRow(GLOBAL_PARK_ID);
        if (isActive(global)) {
            return toPauseState(global);
        }
        DispatchPauseStateEntity park = parkId == null ? null : readRow(parkId);
        if (isActive(park)) {
            return toPauseState(park);
        }
        return new PauseState(false, null, null, null);
    }

    private DispatchPauseStateEntity readRow(long parkId) {
        return pauseStateMapper.selectOne(new LambdaQueryWrapper<DispatchPauseStateEntity>()
                .eq(DispatchPauseStateEntity::getParkId, parkId)
                .last("LIMIT 1"));
    }

    private boolean isActive(DispatchPauseStateEntity entity) {
        return entity != null && Integer.valueOf(1).equals(entity.getIsPaused());
    }

    private PauseState toPauseState(DispatchPauseStateEntity entity) {
        return new PauseState(true, entity.getPauseReason(), entity.getPausedBy(), entity.getPausedAt());
    }

    private boolean isPausedScoped(String cacheKey, long parkId) {
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        boolean paused = readPausedFromDb(parkId);
        stringRedisTemplate.opsForValue().set(cacheKey, paused ? "1" : "0", cacheTtl);
        return paused;
    }

    private boolean readPausedFromDb(long parkId) {
        return isActive(readRow(parkId));
    }

    /** 无行 = 该档从未被操作过 = 不暂停；有行则原地改，并维护 paused_at / resumed_at 的单向语义。 */
    private void persist(long parkId, boolean paused, String reason, String operator) {
        LocalDateTime now = LocalDateTime.now();
        DispatchPauseStateEntity entity = readRow(parkId);
        if (entity == null) {
            entity = new DispatchPauseStateEntity();
            entity.setParkId(parkId);
            entity.setIsPaused(paused ? 1 : 0);
            entity.setPausedBy(operator);
            entity.setPauseReason(reason);
            entity.setPausedAt(paused ? now : null);
            entity.setResumedAt(paused ? null : now);
            pauseStateMapper.insert(entity);
            return;
        }
        entity.setIsPaused(paused ? 1 : 0);
        entity.setPausedBy(operator);
        // 恢复时不传原因就保留上一条：这一行还要解释得清刚结束的那段暂停是谁、为什么下的。
        if (reason != null && !reason.isEmpty()) {
            entity.setPauseReason(reason);
        }
        if (paused) {
            entity.setPausedAt(now);
            entity.setResumedAt(null);
        } else {
            entity.setResumedAt(now);
        }
        pauseStateMapper.updateById(entity);
    }
}
