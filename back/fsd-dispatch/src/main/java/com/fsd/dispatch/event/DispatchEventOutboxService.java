package com.fsd.dispatch.event;

import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import java.util.List;

public interface DispatchEventOutboxService {

    void savePending(DispatchDomainEvent event);

    void markPublished(String eventId);

    void markFailed(String eventId, String lastError);

    List<DispatchEventOutboxEntity> listRetryableEvents(int limit);

    DispatchDomainEvent rebuildDomainEvent(DispatchEventOutboxEntity entity);
}
