package com.fsd.dispatch.event.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import com.fsd.dispatch.event.DispatchEventPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchEventRetrySchedulerTest {

    @Mock
    private DispatchEventOutboxService outboxService;

    @Mock
    private DispatchEventPublisher eventPublisher;

    @Test
    void retryShouldPublishOnlyClaimedEvents() {
        DispatchEventOutboxEntity entity = new DispatchEventOutboxEntity();
        entity.setEventId("evt-1");
        entity.setClaimToken("lease-1");
        DispatchDomainEvent event = DispatchDomainEvent.builder()
                .eventId("evt-1")
                .outboxClaimToken("lease-1")
                .build();
        when(outboxService.claimRetryableEvents(50)).thenReturn(List.of(entity));
        when(outboxService.rebuildDomainEvent(entity)).thenReturn(event);

        new DispatchEventRetryScheduler(outboxService, eventPublisher).retryPublish();

        verify(outboxService).claimRetryableEvents(50);
        verify(eventPublisher).publishEvent(event);
    }
}
