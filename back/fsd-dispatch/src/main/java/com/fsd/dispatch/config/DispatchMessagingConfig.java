package com.fsd.dispatch.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchMessagingConfig {

    public static final String DISPATCH_EXCHANGE = "fsd.dispatch.exchange";
    public static final String DISPATCH_AUDIT_QUEUE = "fsd.dispatch.audit.queue";
    /** 兼容保留：历史共享流队列名（V49 前使用）。 */
    public static final String DISPATCH_STREAM_QUEUE = "fsd.dispatch.stream.queue";
    public static final String DISPATCH_WEBHOOK_QUEUE = "fsd.dispatch.webhook.queue";
    /** 死信拓扑（路线图 §7.4）：投递失败的事件不再原地重投，落到 DLQ 由人处置。 */
    public static final String DISPATCH_DLX = "fsd.dispatch.dlx";
    public static final String DISPATCH_WEBHOOK_DLQ = "fsd.dispatch.webhook.dlq";
    public static final String DISPATCH_WEBHOOK_DEAD_KEY = "dispatch.webhook.dead";

    @Bean
    public TopicExchange dispatchExchange() {
        return new TopicExchange(DISPATCH_EXCHANGE, true, false);
    }

    @Bean
    public Queue dispatchAuditQueue() {
        return new Queue(DISPATCH_AUDIT_QUEUE, true);
    }

    /**
     * 实时流队列改为「每实例专属匿名自动删除队列」（路线图 4.1 多实例就绪）：
     * 共享固定队列会被 RabbitMQ 轮询消费，导致多实例部署下 SSE 事件随机丢失。
     * 匿名队列保证每个实例都收到全量事件并广播给自己的连接；实例下线自动清理。
     */
    @Bean
    public Queue dispatchStreamQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public TopicExchange dispatchDeadLetterExchange() {
        return new TopicExchange(DISPATCH_DLX, true, false);
    }

    /**
     * webhook 队列挂死信：投递失败的消息 nack(requeue=false) 后进 {@link #dispatchWebhookDeadLetterQueue()}。
     *
     * <p>注意这是**队列参数变更**：RabbitMQ 不允许用不同参数重声明已存在的队列（PRECONDITION_FAILED，
     * 直接关通道）。已有环境部署时必须先删掉旧的 {@code fsd.dispatch.webhook.queue} 再启动，
     * 队列里若还有未消费事件要先 purge 或迁移 —— 部署动作由本人执行，见路线图 §7.4。</p>
     */
    @Bean
    public Queue dispatchWebhookQueue() {
        return QueueBuilder.durable(DISPATCH_WEBHOOK_QUEUE)
                .deadLetterExchange(DISPATCH_DLX)
                .deadLetterRoutingKey(DISPATCH_WEBHOOK_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue dispatchWebhookDeadLetterQueue() {
        return new Queue(DISPATCH_WEBHOOK_DLQ, true);
    }

    @Bean
    public Binding dispatchWebhookDeadLetterBinding(Queue dispatchWebhookDeadLetterQueue,
                                                    TopicExchange dispatchDeadLetterExchange) {
        return BindingBuilder.bind(dispatchWebhookDeadLetterQueue).to(dispatchDeadLetterExchange)
                .with(DISPATCH_WEBHOOK_DEAD_KEY);
    }

    @Bean
    public Binding dispatchAuditBinding(Queue dispatchAuditQueue, TopicExchange dispatchExchange) {
        return BindingBuilder.bind(dispatchAuditQueue).to(dispatchExchange).with("dispatch.#");
    }

    @Bean
    public Binding dispatchStreamBinding(Queue dispatchStreamQueue, TopicExchange dispatchExchange) {
        return BindingBuilder.bind(dispatchStreamQueue).to(dispatchExchange).with("dispatch.#");
    }

    @Bean
    public Binding dispatchWebhookBinding(Queue dispatchWebhookQueue, TopicExchange dispatchExchange) {
        return BindingBuilder.bind(dispatchWebhookQueue).to(dispatchExchange).with("dispatch.#");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
