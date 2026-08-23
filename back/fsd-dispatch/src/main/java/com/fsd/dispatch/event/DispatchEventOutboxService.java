package com.fsd.dispatch.event;

import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import java.util.List;

public interface DispatchEventOutboxService {

    void savePending(DispatchDomainEvent event);

    void markPublished(String eventId);

    void markPublished(String eventId, String claimToken);

    void markFailed(String eventId, String lastError);

    void markFailed(String eventId, String lastError, String claimToken);

    String claimEvent(String eventId);

    List<DispatchEventOutboxEntity> claimRetryableEvents(int limit);

    List<DispatchEventOutboxEntity> listRetryableEvents(int limit);

    List<DispatchEventOutboxEntity> listDeadLetterEvents(int limit);

    DispatchDomainEvent rebuildDomainEvent(DispatchEventOutboxEntity entity);
}
