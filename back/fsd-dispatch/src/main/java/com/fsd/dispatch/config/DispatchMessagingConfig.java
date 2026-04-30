package com.fsd.dispatch.config;

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

    @Bean
    public TopicExchange dispatchExchange() {
        return new TopicExchange(DISPATCH_EXCHANGE, true, false);
    }

    @Bean
    public Queue dispatchAuditQueue() {
        return new Queue(DISPATCH_AUDIT_QUEUE, true);
    }

    @Bean
    public Binding dispatchAuditBinding(Queue dispatchAuditQueue, TopicExchange dispatchExchange) {
        return BindingBuilder.bind(dispatchAuditQueue).to(dispatchExchange).with("dispatch.#");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
