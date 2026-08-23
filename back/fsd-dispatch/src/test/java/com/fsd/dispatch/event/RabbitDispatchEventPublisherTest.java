package com.fsd.dispatch.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fsd.dispatch.metrics.DispatchOutboxMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class RabbitDispatchEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private DispatchEventOutboxService outboxService;

    @Test
    void publishEventShouldUseClaimTokenAndMarkPublished() {
        DispatchDomainEvent event = DispatchDomainEvent.builder()
                .eventId("evt-1")
                .outboxClaimToken("lease-1")
                .eventType("dispatch.task.created")
                .businessKey("task-1")
                .build();
        RabbitDispatchEventPublisher publisher = new RabbitDispatchEventPublisher(rabbitTemplate, outboxService,
                new DispatchOutboxMetrics(new SimpleMeterRegistry()));

        publisher.publishEvent(event);

        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        verify(outboxService).markPublished("evt-1", "lease-1");
    }
}
