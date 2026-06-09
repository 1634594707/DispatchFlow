package com.fsd.dispatch.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.dispatch.lock")
public class DispatchLockProperties {

    private Duration ttl = Duration.ofSeconds(10);
}
