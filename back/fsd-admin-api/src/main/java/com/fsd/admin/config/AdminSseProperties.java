package com.fsd.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.admin.sse")
public class AdminSseProperties {

    private long timeoutMs = 300000L;

    private int maxConnections = 100;
}
