package com.fsd.dispatch.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.integration.WebhookDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookDispatchListenerTest {

    @Mock
    private WebhookDeliveryService webhookDeliveryService;

    @Mock
    private DispatchEventConsumeIdempotencyService consumeIdempotencyService;

    @Test
    void duplicateEventShouldNotBeDelivered() {
        DispatchDomainEvent event = DispatchDomainEvent.builder().eventId("evt-1").build();
        when(consumeIdempotencyService.markIfFirstConsume("evt-1")).thenReturn(false);

        new WebhookDispatchListener(webhookDeliveryService, consumeIdempotencyService).onEvent(event);

        verify(webhookDeliveryService, never()).deliver(event);
    }

    @Test
    void firstEventShouldBeDelivered() {
        DispatchDomainEvent event = DispatchDomainEvent.builder().eventId("evt-1").build();
        when(consumeIdempotencyService.markIfFirstConsume("evt-1")).thenReturn(true);

        new WebhookDispatchListener(webhookDeliveryService, consumeIdempotencyService).onEvent(event);

        verify(webhookDeliveryService).deliver(event);
    }

    @Test
    void eventWithoutIdShouldBeIgnored() {
        DispatchDomainEvent event = DispatchDomainEvent.builder().build();

        new WebhookDispatchListener(webhookDeliveryService, consumeIdempotencyService).onEvent(event);

        verify(consumeIdempotencyService, never()).markIfFirstConsume(null);
        verify(webhookDeliveryService, never()).deliver(event);
    }
}
