package com.fsd.dispatch.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.security.FieldEncryptionService;
import com.fsd.dispatch.entity.WebhookDeliveryLogEntity;
import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.mapper.WebhookDeliveryLogMapper;
import com.fsd.dispatch.mapper.WebhookSubscriptionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 §7.4：投递结果必须在 {@code deliver()} 返回之前可知。
 *
 * <p>这一条是端到端跑出来的教训（§13.56）：{@code deliver()} 曾把 HTTP 丢进 4 线程池后立刻返回，
 * 监听器于是无条件 ack —— 投递失败的记录只落在 {@code t_webhook_delivery_log}，消息本身从 broker
 * 消失，DLQ 一条也收不到。下面的用例钉的是"失败必须以异常报回来"，不是失败怎么产生。
 * 全部用 {@code 127.0.0.1} 回调触发 SSRF 守卫，不发任何真实外呼。</p>
 */
class WebhookDeliveryServiceTest {

    private WebhookSubscriptionMapper subscriptionMapper;
    private WebhookDeliveryLogMapper deliveryLogMapper;
    private WebhookDeliveryService service;

    @BeforeEach
    void setUp() {
        subscriptionMapper = mock(WebhookSubscriptionMapper.class);
        deliveryLogMapper = mock(WebhookDeliveryLogMapper.class);
        RobotMessageFormatter formatter = mock(RobotMessageFormatter.class);
        when(formatter.format(any(), any())).thenReturn("{}");
        service = new WebhookDeliveryService(subscriptionMapper, deliveryLogMapper,
                mock(FieldEncryptionService.class), formatter);
    }

    private WebhookSubscriptionEntity subscription(String eventTypes, Integer failures, LocalDateTime lastFailure) {
        WebhookSubscriptionEntity sub = new WebhookSubscriptionEntity();
        sub.setId(77L);
        sub.setName("probe");
        sub.setCallbackUrl("http://127.0.0.1:9/hook");   // 环回 ⇒ SSRF 守卫拦下，无外呼
        sub.setChannelType("GENERIC");
        sub.setEventTypes(eventTypes);
        sub.setEnabled(1);
        sub.setDeleted(0);
        sub.setFailureCount(failures);
        sub.setLastFailureAt(lastFailure);
        return sub;
    }

    private DispatchDomainEvent event() {
        return DispatchDomainEvent.builder()
                .eventId("EV-DELIVER-1")
                .eventType("dispatch.task.created")
                .businessKey("1001")
                .build();
    }

    private void givenSubscriptions(WebhookSubscriptionEntity... subs) {
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(subs));
    }

    @Test
    void unmatchedEventIsNotAFailure() {
        givenSubscriptions(subscription("dispatch.other", 0, null));

        assertDoesNotThrow(() -> service.deliver(event()));
    }

    @Test
    void blockedCallbackCountsAsNotDelivered() {
        givenSubscriptions(subscription("dispatch.task.created", 0, null));

        assertThrows(WebhookDeliveryException.class, () -> service.deliver(event()));
        verify(deliveryLogMapper).insert(any(WebhookDeliveryLogEntity.class));
    }

    /** 熔断打开期间事件同样没送到：必须进 DLQ 等冷却重放，而不是被 ack 掉。 */
    @Test
    void circuitOpenSubscriptionCountsAsNotDelivered() {
        LocalDateTime now = LocalDateTime.now();
        givenSubscriptions(subscription("dispatch.task.created", 5, now));

        assertThrows(WebhookDeliveryException.class, () -> service.deliver(event()));
    }

    /** 中途失败不许中断循环：后面的订阅仍要收到这个事件。 */
    @Test
    void firstFailingSubscriptionDoesNotSkipTheRest() {
        WebhookSubscriptionEntity failing = subscription("dispatch.task.created", 0, null);
        WebhookSubscriptionEntity alsoFailing = subscription("dispatch.#", 0, null);
        givenSubscriptions(failing, alsoFailing);

        WebhookDeliveryException ex =
                assertThrows(WebhookDeliveryException.class, () -> service.deliver(event()));

        // 两条都试过 ⇒ 聚合结论是 2 个匹配、2 个失败
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("2 of 2"), ex.getMessage());
    }
}
