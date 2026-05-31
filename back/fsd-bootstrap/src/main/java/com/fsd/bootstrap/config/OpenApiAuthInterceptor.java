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
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public OpenApiAuthInterceptor(ExternalApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getParameter("apiKey");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("OPEN_API_KEY_REQUIRED", "缺少 API Key");
        }
        ExternalApiKeyEntity entity = apiKeyMapper.selectOne(new LambdaQueryWrapper<ExternalApiKeyEntity>()
                .eq(ExternalApiKeyEntity::getApiKey, apiKey)
                .eq(ExternalApiKeyEntity::getDeleted, 0)
                .eq(ExternalApiKeyEntity::getStatus, "ACTIVE"));
        if (entity == null) {
            throw new BusinessException("OPEN_API_KEY_INVALID", "API Key 无效");
        }
        checkRateLimit(entity);
        entity.setTotalCalls((entity.getTotalCalls() == null ? 0L : entity.getTotalCalls()) + 1);
        entity.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(entity);
        request.setAttribute("openApiKeyId", entity.getId());
        request.setAttribute("openApiKeyName", entity.getKeyName());
        return true;
    }

    private void checkRateLimit(ExternalApiKeyEntity entity) {
        int limit = entity.getRateLimitPerMinute() == null ? 120 : entity.getRateLimitPerMinute();
        long minute = System.currentTimeMillis() / 60_000L;
        String key = entity.getId() + ":" + minute;
        RateWindow window = rateWindows.computeIfAbsent(key, ignored -> new RateWindow(minute));
        if (window.minute != minute) {
            window = new RateWindow(minute);
            rateWindows.put(key, window);
        }
        if (window.count.incrementAndGet() > limit) {
            Long hits = entity.getRateLimitHits() == null ? 0L : entity.getRateLimitHits();
            entity.setRateLimitHits(hits + 1);
            apiKeyMapper.updateById(entity);
            throw new BusinessException("OPEN_API_RATE_LIMIT", "API 调用超过限流");
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
