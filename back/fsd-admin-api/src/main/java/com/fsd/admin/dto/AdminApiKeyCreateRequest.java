package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminApiKeyCreateRequest {

    private String keyName = "external";

    private Integer rateLimitPerMinute = 120;
}
