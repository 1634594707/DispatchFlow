package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminWebhookDeliveryLogResponse {

    private Long id;

    private Long subscriptionId;

    private String eventType;

    private String businessKey;

    private Integer httpStatus;

    private Boolean success;

    private Integer attemptNo;

    private String payloadSummary;

    private String errorMessage;

    private LocalDateTime deliveredAt;
}
