package com.fsd.dispatch.fleet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisFleetRuntimeServiceImpl implements FleetRuntimeService {

    private static final String KEY_PREFIX = "fleet:runtime:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFleetRuntimeServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FleetRuntime> get(Long vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        String json = stringRedisTemplate.opsForValue().get(buildKey(vehicleId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(readJson(json));
    }

    @Override
    public FleetRuntime getOrCreate(Long vehicleId, Supplier<FleetRuntime> supplier) {
        return get(vehicleId).orElseGet(() -> {
            FleetRuntime runtime = supplier.get();
            save(runtime);
            return runtime;
        });
    }

    @Override
    public void save(FleetRuntime runtime) {
        if (runtime == null || runtime.getVehicleId() == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(buildKey(runtime.getVehicleId()), objectMapper.writeValueAsString(runtime), TTL);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("FLEET_RUNTIME_SERIALIZE_FAILED", ex.getMessage());
        }
    }

    @Override
    public Map<Long, FleetRuntime> getBatch(Collection<Long> vehicleIds) {
        Map<Long, FleetRuntime> result = new HashMap<>();
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return result;
        }
        List<Long> ids = vehicleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        List<String> keys = ids.stream().map(this::buildKey).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return result;
        }
        for (int i = 0; i < ids.size(); i++) {
            String json = values.get(i);
            if (json != null && !json.isBlank()) {
                result.put(ids.get(i), readJson(json));
            }
        }
        return result;
    }

    private FleetRuntime readJson(String json) {
        try {
            return objectMapper.readValue(json, FleetRuntime.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("FLEET_RUNTIME_DESERIALIZE_FAILED", ex.getMessage());
        }
    }

    private String buildKey(Long vehicleId) {
        return KEY_PREFIX + vehicleId;
    }
}
