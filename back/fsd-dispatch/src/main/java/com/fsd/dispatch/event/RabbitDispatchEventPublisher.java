package com.fsd.dispatch.event;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitDispatchEventPublisher implements DispatchEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DispatchEventOutboxService outboxService;

    public RabbitDispatchEventPublisher(RabbitTemplate rabbitTemplate, DispatchEventOutboxService outboxService) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
    }

    @Override
    public void publish(String eventType, String businessKey, Object payload) {
        DispatchDomainEvent event = DispatchDomainEvent.of(eventType, businessKey, payload);
        outboxService.savePending(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishEvent(event);
                }
            });
            return;
        }
        publishEvent(event);
    }

    @Override
    public void publishEvent(DispatchDomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    DispatchMessagingConfig.DISPATCH_EXCHANGE,
                    event.getEventType(),
                    event
            );
            outboxService.markPublished(event.getEventId());
        } catch (Exception ex) {
            outboxService.markFailed(event.getEventId(), ex.getMessage());
        }
    }
}
