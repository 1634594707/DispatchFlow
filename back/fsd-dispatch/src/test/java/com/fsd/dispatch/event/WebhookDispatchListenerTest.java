package com.fsd.dispatch.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.integration.WebhookDeliveryService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 §7.4：webhook 消费的 ack 语义。
 *
 * <p>原实现是 AUTO ack + "判重即占位再投递" ⇒ 一次瞬时 DB 抖动就让重投的消息被判成重复、永久失踪。
 * 下面每条断言对应一个失败模式，重点是<b>每条路径都必须有且只有一次 ack/nack</b>（手动模式下漏一条
 * 就是 unacked 堆积）。</p>
 */
class WebhookDispatchListenerTest {

    private WebhookDeliveryService deliveryService;
    private DispatchEventConsumeIdempotencyService idempotency;
    private Channel channel;
    private WebhookDispatchListener listener;

    @BeforeEach
    void setUp() {
        deliveryService = mock(WebhookDeliveryService.class);
        idempotency = mock(DispatchEventConsumeIdempotencyService.class);
        channel = mock(Channel.class);
        listener = new WebhookDispatchListener(deliveryService, idempotency);
    }

    private DispatchDomainEvent event(String eventId) {
        return DispatchDomainEvent.builder()
                .eventId(eventId)
                .eventType("dispatch.task.created")
                .businessKey("1001")
                .build();
    }

    @Test
    void successAcksAndOnlyThenMarksConsumed() throws IOException {
        when(idempotency.isConsumed("EV-1")).thenReturn(false);

        listener.onEvent(event("EV-1"), channel, 11L);

        verify(deliveryService).deliver(any(DispatchDomainEvent.class));
        verify(idempotency).markConsumed("EV-1");
        verify(channel).basicAck(11L, false);
        verify(channel, never()).basicNack(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** 判重必须是只读的：过去 markIfFirstConsume 会在投递之前就占位，崩溃/失败即丢事件。 */
    @Test
    void doesNotOccupyIdempotencySlotBeforeDeliverySucceeds() throws IOException {
        when(idempotency.isConsumed("EV-2")).thenReturn(false);

        listener.onEvent(event("EV-2"), channel, 12L);

        verify(idempotency, never()).markIfFirstConsume(any());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(deliveryService, idempotency);
        order.verify(deliveryService).deliver(any(DispatchDomainEvent.class));
        order.verify(idempotency).markConsumed("EV-2");
    }

    @Test
    void duplicateIsAckedAndNotRedelivered() throws IOException {
        when(idempotency.isConsumed("EV-3")).thenReturn(true);

        listener.onEvent(event("EV-3"), channel, 13L);

        verify(deliveryService, never()).deliver(any());
        verify(channel).basicAck(13L, false);
        verify(idempotency, never()).markConsumed(any());
    }

    @Test
    void deliveryFailureGoesToDeadLetterWithoutRequeueAndLeavesTheEventReplayable() throws IOException {
        when(idempotency.isConsumed("EV-4")).thenReturn(false);
        doThrow(new IllegalStateException("db blip")).when(deliveryService).deliver(any());

        listener.onEvent(event("EV-4"), channel, 14L);

        verify(channel).basicNack(14L, false, false);   // requeue=false ⇒ 进 DLQ，不热循环
        verify(channel, never()).basicAck(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        // 没落幂等标记 ⇒ 从 DLQ 重放时不会被判成重复，这才叫"可人工恢复"
        verify(idempotency, never()).markConsumed(any());
    }

    @Test
    void eventWithoutIdIsDroppedWithAckInsteadOfNackedIntoALoop() throws IOException {
        listener.onEvent(event("  "), channel, 15L);

        verify(deliveryService, never()).deliver(any());
        verify(channel).basicAck(15L, false);
    }

    /**
     * Redis 在 markConsumed 阶段挂掉时必须显式 nack。
     *
     * <p>原实现让异常冒给容器，注释写着"按 requeueRejected=false 送 DLQ"。§13.56 端到端实测两处都假：
     * 仓库根本没有配置 {@code default-requeue-rejected}（默认 true），而 {@code ackMode=MANUAL} 下
     * 容器**不碰投递标签** ⇒ 消息以 unacked 挂在 broker 上，既不进 DLQ 也不再投递，只有消费者掉线才重投。
     * 现在失败在监听器内部就变成结论。</p>
     */
    @Test
    void markFailureNacksInsteadOfParkingTheMessageUnacked() throws IOException {
        when(idempotency.isConsumed("EV-6")).thenReturn(false);
        doThrow(new IllegalStateException("redis down")).when(idempotency).markConsumed("EV-6");

        listener.onEvent(event("EV-6"), channel, 16L);

        verify(channel).basicNack(16L, false, false);
        verify(channel, never()).basicAck(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** 判重这一步挂掉（Redis 不可用）同样不许把消息留成 unacked。 */
    @Test
    void idempotencyCheckFailureNacksToo() throws IOException {
        when(idempotency.isConsumed("EV-7")).thenThrow(new IllegalStateException("redis down"));

        listener.onEvent(event("EV-7"), channel, 17L);

        verify(channel).basicNack(17L, false, false);
        verify(deliveryService, never()).deliver(any());
        verify(channel, never()).basicAck(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** 每条路径有且只有一次结算：ack 与 nack 不得同时发生。 */
    @Test
    void settlesTheDeliveryExactlyOnce() throws IOException {
        when(idempotency.isConsumed("EV-8")).thenReturn(false);

        listener.onEvent(event("EV-8"), channel, 18L);

        verify(channel).basicAck(18L, false);
        verify(channel, never()).basicNack(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * 防回归（实测于 §13.54）：{@code ackMode} 是**字符串**，写成 {@code "manual"} 编译期不报错，
     * 但 Spring 解析时抛 {@code No enum constant AcknowledgeMode.manual} ⇒ 整个应用起不来。
     * 单测全部排除 Rabbit 自动配置，所以这类错误在测试里是隐形的 —— 只能靠这条反射断言钉住。
     */
    @Test
    void listenerAckModeMustBeAValidEnumConstant() {
        int annotated = 0;
        for (java.lang.reflect.Method method : WebhookDispatchListener.class.getDeclaredMethods()) {
            org.springframework.amqp.rabbit.annotation.RabbitListener listener =
                    method.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
            if (listener == null) {
                continue;
            }
            annotated++;
            org.junit.jupiter.api.Assertions.assertEquals(
                    org.springframework.amqp.core.AcknowledgeMode.MANUAL,
                    org.springframework.amqp.core.AcknowledgeMode.valueOf(listener.ackMode()),
                    method.getName() + " 的 ackMode 必须是合法枚举常量名");
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, annotated,
                "监听器注解本身丢了，这条防护就成了空转");
    }
}
