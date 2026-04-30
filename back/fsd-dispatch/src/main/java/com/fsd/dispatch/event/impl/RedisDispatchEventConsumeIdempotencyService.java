package com.fsd.dispatch.event.impl;

import com.fsd.dispatch.event.DispatchEventConsumeIdempotencyService;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisDispatchEventConsumeIdempotencyService implements DispatchEventConsumeIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "fsd:idempotent:event-consume:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDispatchEventConsumeIdempotencyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean markIfFirstConsume(String eventId) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(result);
    }
}
