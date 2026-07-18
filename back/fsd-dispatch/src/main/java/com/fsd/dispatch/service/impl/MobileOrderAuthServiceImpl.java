package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import com.fsd.dispatch.service.MobileOrderAuthService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MobileOrderAuthServiceImpl implements MobileOrderAuthService {

    private static final int MOBILE_RATE_LIMIT_PER_MINUTE = 30;
    private static final String UNSAFE_NO_AUTH_PROP = "fsd.mobile-order.unsafe-no-auth";

    private final ExternalApiKeyMapper apiKeyMapper;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    @Value("${fsd.mobile-order.require-api-key:true}")
    private boolean requireApiKey;

    public MobileOrderAuthServiceImpl(ExternalApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public void validateMobileOrderKey(String apiKey) {
        if (!requireApiKey && Boolean.getBoolean(UNSAFE_NO_AUTH_PROP)) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("MOBILE_ORDER_KEY_REQUIRED", "移动下单需要 X-Mobile-Api-Key");
        }
        ExternalApiKeyEntity entity = apiKeyMapper.selectOne(new LambdaQueryWrapper<ExternalApiKeyEntity>()
                .eq(ExternalApiKeyEntity::getApiKey, apiKey.trim())
                .eq(ExternalApiKeyEntity::getDeleted, 0)
                .eq(ExternalApiKeyEntity::getStatus, "ACTIVE"));
        if (entity == null) {
            throw new BusinessException("MOBILE_ORDER_KEY_INVALID", "移动下单 API Key 无效");
        }
        checkRateLimit(entity);
        entity.setTotalCalls((entity.getTotalCalls() == null ? 0L : entity.getTotalCalls()) + 1);
        apiKeyMapper.updateById(entity);
    }

    private void checkRateLimit(ExternalApiKeyEntity entity) {
        int limit = entity.getRateLimitPerMinute() == null ? MOBILE_RATE_LIMIT_PER_MINUTE
                : Math.min(entity.getRateLimitPerMinute(), MOBILE_RATE_LIMIT_PER_MINUTE);
        long minute = System.currentTimeMillis() / 60_000L;
        String key = "mobile:" + entity.getId() + ":" + minute;
        RateWindow window = rateWindows.computeIfAbsent(key, ignored -> new RateWindow(minute));
        if (window.minute != minute) {
            window = new RateWindow(minute);
            rateWindows.put(key, window);
        }
        if (window.count.incrementAndGet() > limit) {
            long hits = entity.getRateLimitHits() == null ? 0L : entity.getRateLimitHits().longValue();
            entity.setRateLimitHits(hits + 1);
            apiKeyMapper.updateById(entity);
            throw new BusinessException("MOBILE_ORDER_RATE_LIMIT", "移动下单超过限流");
        }
    }

    private static class RateWindow {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private RateWindow(long minute) {
            this.minute = minute;
        }
    }
}
