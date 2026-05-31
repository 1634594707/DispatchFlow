package com.fsd.dispatch.config;

import com.fsd.dispatch.fleet.vda5050.Vda5050MqttProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Vda5050MqttProperties.class)
public class Vda5050Configuration {
}
