package com.fsd.dispatch.service.impl;

import com.fsd.dispatch.service.DispatchPauseControlService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DispatchPauseControlServiceImpl implements DispatchPauseControlService {

    private static final String KEY_GLOBAL = "fsd:dispatch:pause:global";
    private static final String KEY_PARK_PREFIX = "fsd:dispatch:pause:park:";

    private final StringRedisTemplate stringRedisTemplate;

    public DispatchPauseControlServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean isDispatchPaused(Long parkId) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_GLOBAL))) {
            return true;
        }
        if (parkId == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PARK_PREFIX + parkId));
    }

    @Override
    public void setDispatchPaused(Long parkId, boolean paused) {
        if (parkId == null) {
            if (paused) {
                stringRedisTemplate.opsForValue().set(KEY_GLOBAL, "1");
            } else {
                stringRedisTemplate.delete(KEY_GLOBAL);
            }
            return;
        }
        String key = KEY_PARK_PREFIX + parkId;
        if (paused) {
            stringRedisTemplate.opsForValue().set(key, "1");
        } else {
            stringRedisTemplate.delete(key);
        }
    }

    @Override
    public boolean isGlobalDispatchPaused() {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_GLOBAL));
    }
}
