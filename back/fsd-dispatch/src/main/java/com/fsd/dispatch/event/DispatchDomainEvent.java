package com.fsd.dispatch.event;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchDomainEvent implements Serializable {

    private String eventId;

    private String eventType;

    private LocalDateTime eventTime;

    private String businessKey;

    private Object payload;

    public static DispatchDomainEvent of(String eventType, String businessKey, Object payload) {
        return DispatchDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventTime(LocalDateTime.now())
                .businessKey(businessKey)
                .payload(payload)
                .build();
    }
}
