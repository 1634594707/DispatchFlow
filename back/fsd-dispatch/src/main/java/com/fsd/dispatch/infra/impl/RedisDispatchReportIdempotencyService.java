package com.fsd.dispatch.infra.impl;

import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisDispatchReportIdempotencyService implements DispatchReportIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(6);
    private static final String KEY_PREFIX = "fsd:idempotent:state-report:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDispatchReportIdempotencyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean markIfFirstReport(VehicleReportRequest request) {
        String key = KEY_PREFIX + request.getVehicleCode() + ":" + request.getReportType() + ":" + request.getReportTime();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(result);
    }
}
