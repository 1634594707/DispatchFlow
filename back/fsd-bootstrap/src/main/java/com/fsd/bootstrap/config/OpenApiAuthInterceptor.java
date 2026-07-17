package com.fsd.bootstrap.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    private final ExternalApiKeyMapper apiKeyMapper;
    // TODO(B007): single-instance rate limiter — counters live in this JVM only.
    // Multi-instance deployments must migrate to Redis (e.g. INCR + EXPIRE or Lua sliding window)
    // so rate-limit state is shared across nodes.
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public OpenApiAuthInterceptor(ExternalApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SEC-04 fix: API Key must be supplied via the X-Api-Key header only.
        // Query-string credentials are rejected because they leak into access logs,
        // browser history, and referrer headers.
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("OPEN_API_KEY_REQUIRED", "缺少 API Key (请通过 X-Api-Key 请求头传递)");
        }
        ExternalApiKeyEntity entity = apiKeyMapper.selectOne(new LambdaQueryWrapper<ExternalApiKeyEntity>()
                .eq(ExternalApiKeyEntity::getApiKey, apiKey)
                .eq(ExternalApiKeyEntity::getDeleted, 0)
                .eq(ExternalApiKeyEntity::getStatus, "ACTIVE"));
        if (entity == null) {
            throw new BusinessException("OPEN_API_KEY_INVALID", "API Key 无效");
        }
        checkRateLimit(entity);
        // SEC-18 fix: throttle DB writes for lastUsedAt/totalCalls. Update at most once
        // per minute per key to avoid a write-per-request bottleneck under load.
        long now = System.currentTimeMillis();
        Long lastUsedMs = entity.getLastUsedAt() == null ? null
                : entity.getLastUsedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
        if (lastUsedMs == null || (now - lastUsedMs) >= 60_000L) {
            entity.setTotalCalls((entity.getTotalCalls() == null ? 0L : entity.getTotalCalls()) + 1);
            entity.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(entity);
        }
        request.setAttribute("openApiKeyId", entity.getId());
        request.setAttribute("openApiKeyName", entity.getKeyName());
        return true;
    }

    private void checkRateLimit(ExternalApiKeyEntity entity) {
        int limit = entity.getRateLimitPerMinute() == null ? 120 : entity.getRateLimitPerMinute();
        long minute = System.currentTimeMillis() / 60_000L;
        String key = entity.getId() + ":" + minute;
        // Atomic create-or-reuse via compute: avoids the computeIfAbsent + put race
        // and guarantees a consistent window across concurrent threads.
        RateWindow window = rateWindows.compute(key, (k, existing) ->
                (existing != null && existing.minute == minute) ? existing : new RateWindow(minute));
        if (window.count.incrementAndGet() > limit) {
            long hits = entity.getRateLimitHits() == null ? 0L : entity.getRateLimitHits().longValue();
            entity.setRateLimitHits(hits + 1);
            apiKeyMapper.updateById(entity);
            throw new BusinessException("OPEN_API_RATE_LIMIT", "API 调用超过限流");
        }
        cleanupExpiredWindows(minute);
    }

    private void cleanupExpiredWindows(long currentMinute) {
        // Bound memory: drop windows whose minute has rolled over. Only runs when the map
        // has accumulated enough stale entries to justify the sweep.
        if (rateWindows.size() < 128) {
            return;
        }
        rateWindows.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute);
    }

    private static class RateWindow {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private RateWindow(long minute) {
            this.minute = minute;
        }
    }
}
