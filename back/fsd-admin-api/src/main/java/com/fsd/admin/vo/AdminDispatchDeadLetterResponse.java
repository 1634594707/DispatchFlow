package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDispatchDeadLetterResponse {

    private Long id;

    private String eventId;

    private String eventType;

    private String businessKey;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
