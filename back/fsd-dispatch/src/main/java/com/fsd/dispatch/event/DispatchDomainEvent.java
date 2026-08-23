package com.fsd.dispatch.event;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.UUID;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchDomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;

    private String eventType;

    private LocalDateTime eventTime;

    private String businessKey;

    private Object payload;

    private Long parkId;

    @Builder.Default
    private Integer eventVersion = 1;

    /** Internal outbox lease token; never serialized to RabbitMQ consumers. */
    @JsonIgnore
    private String outboxClaimToken;

    public static DispatchDomainEvent of(String eventType, String businessKey, Object payload) {
        Long parkId = null;
        if (payload instanceof Map<?, ?> values && values.get("parkId") instanceof Number number) {
            parkId = number.longValue();
        }
        return DispatchDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventTime(LocalDateTime.now())
                .businessKey(businessKey)
                .payload(payload)
                .parkId(parkId)
                .build();
    }
}
