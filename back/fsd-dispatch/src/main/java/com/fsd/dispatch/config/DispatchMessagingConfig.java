package com.fsd.dispatch.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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
    public Queue dispatchWebhookQueue() {
        return new Queue(DISPATCH_WEBHOOK_QUEUE, true);
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
