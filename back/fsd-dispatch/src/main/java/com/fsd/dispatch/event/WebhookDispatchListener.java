package com.fsd.dispatch.event;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.integration.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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

    @RabbitListener(queues = DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE)
    public void onEvent(DispatchDomainEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            log.warn("Ignore webhook event without eventId");
            return;
        }
        if (!consumeIdempotencyService.markIfFirstConsume(event.getEventId())) {
            log.info("Ignore duplicate webhook dispatch event, eventId={}", event.getEventId());
            return;
        }
        webhookDeliveryService.deliver(event);
    }
}
