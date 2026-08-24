package com.fsd.bootstrap.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables operational schedulers by default while allowing integration tests to
 * disable them with {@code spring.task.scheduling.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.task.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
