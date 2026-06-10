package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminExternalApiKeyResponse {

    private Long id;

    private String keyName;

    private String apiKey;

    private String status;

    private Integer rateLimitPerMinute;

    private Long totalCalls;

    private Long rateLimitHits;

    private LocalDateTime lastUsedAt;
}
