package com.fsd.dispatch.infra;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P1-3 仪表化：Lettuce 命令延迟接进 Micrometer。
 *
 * <p>此前压测读数缺"Redis RTT"这一格——k6 只看 HTTP 层，actuator 里也没有 Redis 客户端指标。
 * 挂上 {@link MicrometerCommandLatencyRecorder} 后，{@code lettuce.command.latency*}
 * 按 command 类型（GET/SET/EXEC/…）与节点落到 /internal/actuator/prometheus，
 * 压测读数四格齐：HTTP / Outbox / 锁 / Redis。
 *
 * <p>Spring Boot 的 Lettuce 自动装配发现容器里有 {@link ClientResources} bean 就会复用它，
 * 不需要改 {@code RedisConnectionFactory} 的任何调用方。
 * 6.3.2 的记录器吃的是 {@link MicrometerOptions}（不是 CommandLatencyCollectorOptions——
 * 那个构造器签名是 6.5+ 的，本仓 lettuce-core 6.3.2 上会编译不过，踩过一次）。
 */
@Configuration
public class RedisObservabilityConfig {

    @Bean
    public ClientResources clientResources(MeterRegistry registry) {
        return DefaultClientResources.builder()
                .commandLatencyRecorder(new MicrometerCommandLatencyRecorder(
                        registry, MicrometerOptions.builder().enable().build()))
                .build();
    }
}
