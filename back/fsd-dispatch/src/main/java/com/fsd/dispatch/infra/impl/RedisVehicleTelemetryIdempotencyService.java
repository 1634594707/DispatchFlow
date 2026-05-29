package com.fsd.dispatch.infra.impl;

import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisVehicleTelemetryIdempotencyService implements VehicleTelemetryIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(6);
    private static final String KEY_PREFIX = "fsd:idempotent:telemetry:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisVehicleTelemetryIdempotencyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean markIfFirstTelemetry(VehicleTelemetryRequest request) {
        String key = KEY_PREFIX + request.getVehicleCode() + ":" + request.getEventSeq();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(result);
    }
}
