package com.fsd.dispatch.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.service.TrafficZoneControlService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrafficZoneControlServiceImpl implements TrafficZoneControlService {

    private static final String KEY_PREFIX = "fsd:traffic:pause:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<PauseZone>> memoryStore = new ConcurrentHashMap<>();

    public TrafficZoneControlServiceImpl(ObjectProvider<StringRedisTemplate> redisProvider,
                                         ObjectMapper objectMapper) {
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PauseZone> listPauseZones(Long parkId) {
        Long resolved = parkId != null ? parkId : 0L;
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(KEY_PREFIX + resolved);
                if (raw == null || raw.isBlank()) {
                    return List.of();
                }
                return objectMapper.readValue(raw, new TypeReference<List<PauseZone>>() {
                });
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.copyOf(memoryStore.getOrDefault(resolved, new CopyOnWriteArrayList<>()));
    }

    @Override
    public PauseZone addPauseZone(Long parkId, double minX, double minY, double maxX, double maxY, String label) {
        Long resolved = parkId != null ? parkId : 0L;
        double loX = Math.min(minX, maxX);
        double hiX = Math.max(minX, maxX);
        double loY = Math.min(minY, maxY);
        double hiY = Math.max(minY, maxY);
        PauseZone zone = new PauseZone(loX, loY, hiX, hiY, label == null ? "管制区" : label);
        List<PauseZone> zones = new ArrayList<>(listPauseZones(resolved));
        zones.add(zone);
        persist(resolved, zones);
        return zone;
    }

    @Override
    public void clearPauseZones(Long parkId) {
        Long resolved = parkId != null ? parkId : 0L;
        persist(resolved, List.of());
    }

    @Override
    public boolean isPointInPausedZone(Long parkId, BigDecimal x, BigDecimal y) {
        if (x == null || y == null) {
            return false;
        }
        double px = x.doubleValue();
        double py = y.doubleValue();
        for (PauseZone zone : listPauseZones(parkId)) {
            if (px >= zone.minX() && px <= zone.maxX() && py >= zone.minY() && py <= zone.maxY()) {
                return true;
            }
        }
        return false;
    }

    private void persist(Long parkId, List<PauseZone> zones) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().set(KEY_PREFIX + parkId, objectMapper.writeValueAsString(zones));
                return;
            } catch (Exception ignored) {
                // fall through to memory
            }
        }
        memoryStore.put(parkId, new CopyOnWriteArrayList<>(zones));
    }
}
