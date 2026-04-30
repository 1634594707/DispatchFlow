package com.fsd.dispatch.event.impl;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventConsumeIdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DispatchEventAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventAuditListener.class);

    private final DispatchEventConsumeIdempotencyService consumeIdempotencyService;

    public DispatchEventAuditListener(DispatchEventConsumeIdempotencyService consumeIdempotencyService) {
        this.consumeIdempotencyService = consumeIdempotencyService;
    }

    @RabbitListener(queues = DispatchMessagingConfig.DISPATCH_AUDIT_QUEUE)
    public void onEvent(DispatchDomainEvent event) {
        if (!consumeIdempotencyService.markIfFirstConsume(event.getEventId())) {
            log.info("Ignore duplicate dispatch event, eventId={}", event.getEventId());
            return;
        }
        log.info("Dispatch audit event consumed, eventType={}, businessKey={}, eventId={}",
                event.getEventType(), event.getBusinessKey(), event.getEventId());
    }
}
