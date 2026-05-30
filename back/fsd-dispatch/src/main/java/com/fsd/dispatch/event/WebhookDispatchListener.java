package com.fsd.dispatch.event;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.integration.WebhookDeliveryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WebhookDispatchListener {

    private final WebhookDeliveryService webhookDeliveryService;

    public WebhookDispatchListener(WebhookDeliveryService webhookDeliveryService) {
        this.webhookDeliveryService = webhookDeliveryService;
    }

    @RabbitListener(queues = DispatchMessagingConfig.DISPATCH_WEBHOOK_QUEUE)
    public void onEvent(DispatchDomainEvent event) {
        webhookDeliveryService.deliver(event);
    }
}
