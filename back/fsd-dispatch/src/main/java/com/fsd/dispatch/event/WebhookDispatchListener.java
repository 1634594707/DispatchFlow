package com.fsd.dispatch.event;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.integration.WebhookDeliveryService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * webhook 事件消费（路线图 §7.4 消息可靠性）。
 *
 * <p>三处刻意的顺序，都是为了不再"静默永久丢"：</p>
 * <ol>
 *   <li><b>手动 ack</b>（{@code ackMode = "MANUAL"}，枚举常量名大写）：容器不再替我们决定重投还是丢弃。
 *       不用第二个 {@code SimpleRabbitListenerContainerFactory} 承载它，是因为再声明一个同类型
 *       工厂会让 Spring Boot 的默认 {@code rabbitListenerContainerFactory} 条件退避，
 *       连带改掉 audit / stream 两个消费者的行为。</li>
 *   <li><b>先投递、成功后才落幂等标记</b>：原来的"边判重边占位"配 AUTO ack，等于一次瞬时 DB 抖动
 *       就把事件判成重复、重投时被跳过 ⇒ 消息永久失踪。</li>
 *   <li><b>判重、投递、落标记全在同一段 try 里</b>：手动模式下容器**不会**替我们结算投递标签。
 *       §13.56 端到端实测：把 Redis 关掉后 {@code isConsumed} 抛出的异常冒到容器，
 *       {@code ConditionalRejectingErrorHandler} 判定非致命、但 MANUAL 模式下无人 nack
 *       ⇒ 消息以 unacked 挂在 broker 上（队列 1 条 / 未确认 1 条），既不进 DLQ 也不再投递，
 *       只有消费者掉线才会重投。所以失败必须在这里显式 {@code basicNack(requeue=false)}。</li>
 * </ol>
 *
 * <p>投递失败（含熔断打开、SSRF 拦截）由 {@link WebhookDeliveryService#deliver} 抛
 * {@code WebhookDeliveryException} 报回来 —— 它过去把 HTTP 丢进线程池后立刻返回，
 * 于是"失败"永远传不到这里，消息被当作成功 ack（§13.56）。</p>
 *
 * <p>代价说清楚：幂等标记写失败发生在**投递成功之后**，这条会 nack 进 DLQ，人工重放时
 * 订阅方会收到**重复通知**（webhook 本就是 at-least-once）。反过来选择 ack 则是"标记没落上、
 * 但消息已消失"，两者都不可消除，只选可人工处置的那个。</p>
 */
@Component
public class WebhookDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchListener.class);

    private final WebhookDeliveryService webhookDeliveryService;
    private final DispatchEventConsumeIdempotencyService consumeIdempotencyService;

    public WebhookDispatchListener(WebhookDeliveryService webhookDeliveryService,
                                   DispatchEventConsumeIdempotencyService consumeIdempotencyService) {
        this.webhookDeliveryService = webhookDeliveryService;
        this.consumeIdempotencyService = consumeIdempotencyService;
    }

    @RabbitListener(queues = DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE, ackMode = "MANUAL")
    public void onEvent(DispatchDomainEvent event, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            log.warn("Ignore webhook event without eventId");
            channel.basicAck(deliveryTag, false);   // 无主消息：ack 丢掉，别 nack 成热循环
            return;
        }
        if (process(event)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        // requeue=false ⇒ 进 DLQ（fsd.dispatch.webhook.dlq）等人处置，既不热循环也不丢。
        channel.basicNack(deliveryTag, false, false);
    }

    /** {@code false} = 这次没能交付，必须让消息进 DLQ；异常一律在这里变成结论，不外溢。 */
    private boolean process(DispatchDomainEvent event) {
        try {
            if (consumeIdempotencyService.isConsumed(event.getEventId())) {
                log.info("Ignore duplicate webhook dispatch event, eventId={}", event.getEventId());
                return true;
            }
            webhookDeliveryService.deliver(event);
        } catch (RuntimeException ex) {
            log.error("Webhook consumption failed, sending to DLQ. eventId={}", event.getEventId(), ex);
            return false;
        }
        // 标记放在投递之后：进程若在 deliver 前崩溃，消息仍在队列里可重投，不会被判成重复而消失。
        try {
            consumeIdempotencyService.markConsumed(event.getEventId());
        } catch (RuntimeException ex) {
            log.error("Webhook delivered but idempotency mark failed, sending to DLQ. eventId={}",
                    event.getEventId(), ex);
            return false;
        }
        return true;
    }
}
