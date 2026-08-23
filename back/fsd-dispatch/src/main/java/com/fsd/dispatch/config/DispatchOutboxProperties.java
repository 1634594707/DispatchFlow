package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Retry and dead-letter policy for the dispatch event outbox. */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.dispatch.outbox")
public class DispatchOutboxProperties {

    private int maxRetries = 5;

    private long leaseSeconds = 60L;
}
