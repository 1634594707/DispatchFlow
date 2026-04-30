package com.fsd.dispatch.event;

public interface DispatchEventPublisher {

    void publish(String eventType, String businessKey, Object payload);

    void publishEvent(DispatchDomainEvent event);
}
