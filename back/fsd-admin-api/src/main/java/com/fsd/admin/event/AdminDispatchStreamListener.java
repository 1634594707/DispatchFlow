package com.fsd.admin.event;

import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventType;
import java.time.Instant;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AdminDispatchStreamListener {

    private final AdminDispatchStreamService streamService;

    public AdminDispatchStreamListener(AdminDispatchStreamService streamService) {
        this.streamService = streamService;
    }

    @RabbitListener(queues = DispatchMessagingConfig.DISPATCH_STREAM_QUEUE)
    public void onEvent(DispatchDomainEvent event) {
        if (!streamService.hasClients()) {
            return;
        }
        String eventType = event.getEventType();
        streamService.broadcast("event", Map.of(
                "eventType", eventType,
                "businessKey", event.getBusinessKey(),
                "eventTime", event.getEventTime(),
                "ts", Instant.now().toString()
        ));

        if (DispatchEventType.EXCEPTION_OPEN.equals(eventType)) {
            streamService.broadcast("exception", Map.of(
                    "eventType", eventType,
                    "businessKey", event.getBusinessKey(),
                    "payload", event.getPayload(),
                    "eventTime", event.getEventTime(),
                    "ts", Instant.now().toString()
            ));
        }

        if (isWorkbenchEvent(eventType)) {
            streamService.broadcast("workbench-refresh", Map.of("ts", Instant.now().toString()));
        }
        if (isDashboardEvent(eventType)) {
            streamService.broadcast("dashboard-refresh", Map.of("ts", Instant.now().toString()));
        }
    }

    private boolean isWorkbenchEvent(String eventType) {
        return DispatchEventType.TASK_CREATED.equals(eventType)
                || DispatchEventType.TASK_ASSIGNED.equals(eventType)
                || DispatchEventType.TASK_MANUAL_ASSIGNED.equals(eventType)
                || DispatchEventType.TASK_MANUAL_PENDING.equals(eventType)
                || DispatchEventType.TASK_CANCELLED.equals(eventType)
                || DispatchEventType.EXCEPTION_OPEN.equals(eventType)
                || DispatchEventType.EXCEPTION_RESOLVED.equals(eventType);
    }

    private boolean isDashboardEvent(String eventType) {
        return DispatchEventType.TASK_EXECUTING.equals(eventType)
                || DispatchEventType.TASK_SUCCESS.equals(eventType)
                || DispatchEventType.TASK_FAILED.equals(eventType)
                || DispatchEventType.TASK_CREATED.equals(eventType)
                || DispatchEventType.TASK_CANCELLED.equals(eventType)
                || DispatchEventType.EXCEPTION_OPEN.equals(eventType);
    }
}
