package com.fsd.dispatch.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DispatchDomainEventTest {

    @Test
    void eventFactoryShouldExtractParkScopeAndSetVersion() {
        DispatchDomainEvent event = DispatchDomainEvent.of(
                DispatchEventType.TASK_CREATED,
                "task-1",
                Map.of("parkId", 9L));

        assertEquals(9L, event.getParkId());
        assertEquals(1, event.getEventVersion());
        assertEquals(DispatchEventType.TASK_CREATED, event.getEventType());
    }
}
