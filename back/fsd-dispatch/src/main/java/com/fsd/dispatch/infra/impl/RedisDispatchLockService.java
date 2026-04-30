package com.fsd.dispatch.infra.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.infra.DispatchLockService;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisDispatchLockService implements DispatchLockService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final String LOCK_KEY_PREFIX = "fsd:lock:dispatch:task:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDispatchLockService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String acquireTaskLock(Long taskId) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(buildLockKey(taskId), lockToken, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("DISPATCH_TASK_LOCKED", "Dispatch task is being processed");
        }
        return lockToken;
    }

    @Override
    public void releaseTaskLock(Long taskId, String lockToken) {
        stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(buildLockKey(taskId)), lockToken);
    }

    private String buildLockKey(Long taskId) {
        return LOCK_KEY_PREFIX + taskId;
    }
}
