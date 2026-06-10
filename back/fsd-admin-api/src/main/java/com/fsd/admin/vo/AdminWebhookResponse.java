package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminWebhookResponse {

    private Long id;

    private String name;

    private String callbackUrl;

    private String eventTypes;

    private boolean enabled;

    private Integer failureCount;

    private LocalDateTime lastDeliveryAt;
}
