package com.fsd.dispatch.fleet.policy;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 遥测新鲜度统一判定规则（路线图 5.1）。
 *
 * <p>快照组装、可派判定和前端数据年龄展示共用同一阈值；Redis 运行态 TTL 只是保留时间，
 * 不作为在线或可派依据。</p>
 */
@Component
public class TelemetryFreshnessPolicy {

    private final Duration staleAfter;

    public TelemetryFreshnessPolicy(
            @Value("${fsd.dispatch.telemetry.stale-seconds:30}") long staleSeconds) {
        this.staleAfter = Duration.ofSeconds(Math.max(1L, staleSeconds));
    }

    /** 超过阈值（含从未上报）视为过期。 */
    public boolean isStale(LocalDateTime lastTelemetryAt) {
        return lastTelemetryAt == null || isStale(lastTelemetryAt, LocalDateTime.now());
    }

    public boolean isStale(LocalDateTime lastTelemetryAt, LocalDateTime now) {
        return lastTelemetryAt == null || lastTelemetryAt.isBefore(now.minus(staleAfter));
    }

    /** 数据年龄（秒）；从未上报返回 null。 */
    public Long ageSeconds(LocalDateTime lastTelemetryAt) {
        return ageSeconds(lastTelemetryAt, LocalDateTime.now());
    }

    public Long ageSeconds(LocalDateTime lastTelemetryAt, LocalDateTime now) {
        return lastTelemetryAt == null ? null : Math.max(0, Duration.between(lastTelemetryAt, now).getSeconds());
    }

    public Duration threshold() {
        return staleAfter;
    }
}