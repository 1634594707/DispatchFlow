package com.fsd.dispatch.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 熔断器冷却半开的契约（§7.2「Webhook 熔断只开不关」）。
 * 这条是接外部决策 API（Jev）的前置条件：降级路径本身不能是单向门。
 */
class WebhookCircuitBreakerTest {

    private static final int THRESHOLD = 5;
    private static final long COOLDOWN_SECONDS = 300L;

    private final WebhookDeliveryService service = newService();

    private static WebhookDeliveryService newService() {
        WebhookDeliveryService instance = new WebhookDeliveryService(null, null, null, null);
        ReflectionTestUtils.setField(instance, "circuitCooldownSeconds", COOLDOWN_SECONDS);
        return instance;
    }

    private static WebhookSubscriptionEntity subscription(Integer failures, LocalDateTime lastFailureAt) {
        WebhookSubscriptionEntity sub = new WebhookSubscriptionEntity();
        sub.setFailureCount(failures);
        sub.setLastFailureAt(lastFailureAt);
        return sub;
    }

    @Test
    void belowThresholdStaysClosed() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        assertFalse(service.isCircuitOpen(subscription(THRESHOLD - 1, now), now));
        assertFalse(service.isCircuitOpen(subscription(null, null), now));
    }

    @Test
    void openImmediatelyAfterReachingThreshold() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        assertTrue(service.isCircuitOpen(subscription(THRESHOLD, now), now));
        assertTrue(service.isCircuitOpen(subscription(THRESHOLD + 4, now.plusSeconds(COOLDOWN_SECONDS - 1)), now));
    }

    @Test
    void halfOpensOnceTheCooldownWindowElapses() {
        LocalDateTime failure = LocalDateTime.of(2026, 9, 21, 12, 0);
        // 修复前这里是永久 true：failure_count 只会由管理端编辑或投递成功归零，
        // 而投递成功在熔断打开后不可达，订阅因此永久静默。
        assertFalse(service.isCircuitOpen(
                subscription(THRESHOLD, failure), failure.plusSeconds(COOLDOWN_SECONDS + 1)));
    }

    @Test
    void legacyRowWithoutFailureTimestampStaysOpenUntilItFailsAgain() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        assertTrue(service.isCircuitOpen(subscription(THRESHOLD, null), now));
    }
}
