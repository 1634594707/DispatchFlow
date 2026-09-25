package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import com.fsd.dispatch.service.MobileOrderAuthService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MobileOrderAuthServiceImpl implements MobileOrderAuthService {

    private static final Logger log = LoggerFactory.getLogger(MobileOrderAuthServiceImpl.class);

    private static final int MOBILE_RATE_LIMIT_PER_MINUTE = 30;
    private static final String UNSAFE_NO_AUTH_PROP = "fsd.mobile-order.unsafe-no-auth";

    private final ExternalApiKeyMapper apiKeyMapper;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    @Value("${fsd.mobile-order.require-api-key:true}")
    private boolean requireApiKey;

    /**
     * ⚠ 这里原来写的是 {@code Boolean.getBoolean(UNSAFE_NO_AUTH_PROP)} —— 它读的是 **JVM 系统属性**
     * （只能 {@code -Dfsd.mobile-order.unsafe-no-auth=true}），而启动脚本 export 的是**环境变量**，
     * 于是 {@code MOBILE_ORDER_REQUIRE_KEY=false} 永远凑不齐第二个条件 ⇒
     * 脚本注释里"本地已关掉移动端下单密钥"是一句**谎**：手机页在本地和线上都拿不到站点，
     * 全部返回 {@code MOBILE_ORDER_KEY_REQUIRED}。改成 Spring 属性后开关才真的能拨。
     *
     * <p>默认仍是 {@code false}：要对外开放下单必须**两个都显式关掉**，且放行时会在日志里留一行 WARN，
     * 让"这个端点现在匿名可写"这件事在运行态可见。
     */
    @Value("${" + UNSAFE_NO_AUTH_PROP + ":false}")
    private boolean unsafeNoAuth;

    public MobileOrderAuthServiceImpl(ExternalApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public void validateMobileOrderKey(String apiKey) {
        if (!requireApiKey && unsafeNoAuth) {
            log.warn("移动端下单密钥校验被显式关闭（require-api-key=false 且 unsafe-no-auth=true）："
                    + "该端点现在匿名可下单，只应在演示/本机使用");
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
