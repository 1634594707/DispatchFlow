package com.fsd.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FieldEncryptionProperties.class)
public class FieldEncryptionConfiguration {
}
