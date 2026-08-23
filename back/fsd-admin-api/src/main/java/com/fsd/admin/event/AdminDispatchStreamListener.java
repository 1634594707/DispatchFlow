package com.fsd.admin.event;

import com.fsd.admin.scheduler.OpenExceptionEscalationScheduler;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventType;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AdminDispatchStreamListener {

    private final AdminDispatchStreamService streamService;

    public AdminDispatchStreamListener(AdminDispatchStreamService streamService) {
        this.streamService = streamService;
    }

    /** SpEL 引用匿名队列：每实例独立队列，多实例下各自收到全量流事件（路线图 4.1）。 */
    @RabbitListener(queues = "#{dispatchStreamQueue.name}")
    public void onEvent(DispatchDomainEvent event) {
        if (!streamService.hasClients()) {
            return;
        }
        String eventType = event.getEventType();
        streamService.broadcast("event", envelope(event, null), event.getParkId());

        if (DispatchEventType.EXCEPTION_OPEN.equals(eventType)) {
            streamService.broadcast("exception", envelope(event, event.getPayload()), event.getParkId());
        }

        if (OpenExceptionEscalationScheduler.EVENT_ESCALATED.equals(eventType)) {
            streamService.broadcast("exception-escalated", envelope(event, event.getPayload()), event.getParkId());
        }

        if (isWorkbenchEvent(eventType)) {
            streamService.broadcast("workbench-refresh", envelope(event, null), event.getParkId());
        }
        if (isDashboardEvent(eventType)) {
            streamService.broadcast("dashboard-refresh", envelope(event, null), event.getParkId());
        }
    }

    private Map<String, Object> envelope(DispatchDomainEvent event, Object payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.getEventId());
        result.put("eventType", event.getEventType());
        result.put("businessKey", event.getBusinessKey());
        result.put("parkId", event.getParkId());
        result.put("eventTime", event.getEventTime());
        result.put("eventVersion", event.getEventVersion());
        result.put("ts", Instant.now().toString());
        if (payload != null) result.put("payload", payload);
        return result;
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
