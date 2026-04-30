package com.fsd.dispatch.event.impl;

import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import com.fsd.dispatch.event.DispatchEventPublisher;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fsd.dispatch.outbox.retry-enabled", havingValue = "true", matchIfMissing = true)
public class DispatchEventRetryScheduler {

    private final DispatchEventOutboxService outboxService;
    private final DispatchEventPublisher eventPublisher;

    public DispatchEventRetryScheduler(DispatchEventOutboxService outboxService,
                                       DispatchEventPublisher eventPublisher) {
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${fsd.dispatch.outbox.retry-delay-ms:30000}")
    public void retryPublish() {
        List<DispatchEventOutboxEntity> retryableEvents = outboxService.listRetryableEvents(50);
        for (DispatchEventOutboxEntity entity : retryableEvents) {
            DispatchDomainEvent event = outboxService.rebuildDomainEvent(entity);
            eventPublisher.publishEvent(event);
        }
    }
}
