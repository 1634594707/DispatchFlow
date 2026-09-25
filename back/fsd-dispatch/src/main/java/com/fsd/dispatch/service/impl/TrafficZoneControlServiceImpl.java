package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.entity.TrafficPauseZoneEntity;
import com.fsd.dispatch.mapper.TrafficPauseZoneMapper;
import com.fsd.dispatch.service.TrafficZoneControlService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 交通管制暂停区（§7.4）：<b>MySQL = 真相</b>，Redis 只做<b>带 TTL 的运行态缓存</b>。
 *
 * <p>修复前三条隐患一并解决：① 裸 {@code set} 无 TTL；② 无落库来源，Redis 不可从真相重建；
 * ③ JVM 内存兜底 {@code ConcurrentHashMap} 跨重启/多副本不一致（且是唯一"消失即失效"的假兜底）。
 * 现读走 cache-aside（miss 回真相表并回填缓存），写走「先落库、再刷新缓存」。
 * Redis 不可用时直接读库，功能不因缺缓存而退化，只是少了缓存加速。
 */
@Slf4j
@Service
public class TrafficZoneControlServiceImpl implements TrafficZoneControlService {

    private static final String KEY_PREFIX = "fsd:traffic:pause:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final TrafficPauseZoneMapper zoneMapper;
    private final Duration cacheTtl;

    public TrafficZoneControlServiceImpl(ObjectProvider<StringRedisTemplate> redisProvider,
                                         ObjectMapper objectMapper,
                                         TrafficPauseZoneMapper zoneMapper,
                                         @Value("${fsd.traffic.pause-cache-ttl-seconds:900}") long cacheTtlSeconds) {
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.zoneMapper = zoneMapper;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Override
    public List<PauseZone> listPauseZones(Long parkId) {
        Long resolved = parkId != null ? parkId : 0L;
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            String raw = redis.opsForValue().get(KEY_PREFIX + resolved);
            if (raw != null && !raw.isBlank()) {
                try {
                    return objectMapper.readValue(raw, new TypeReference<List<PauseZone>>() {
                    });
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    // 缓存脏值：当作 miss 回落真相表并重建
                    log.debug("traffic pause cache unreadable, falling back to truth table: {}", ex.toString());
                }
            }
        }
        List<PauseZone> zones = fromDatabase(resolved);
        writeCache(redis, resolved, zones);
        return zones;
    }

    @Override
    public PauseZone addPauseZone(Long parkId, double minX, double minY, double maxX, double maxY, String label) {
        Long resolved = parkId != null ? parkId : 0L;
        double loX = Math.min(minX, maxX);
        double hiX = Math.max(minX, maxX);
        double loY = Math.min(minY, maxY);
        double hiY = Math.max(minY, maxY);
        String zoneLabel = label == null ? "管制区" : label;

        TrafficPauseZoneEntity entity = new TrafficPauseZoneEntity();
        entity.setParkId(resolved);
        entity.setMinX(loX);
        entity.setMinY(loY);
        entity.setMaxX(hiX);
        entity.setMaxY(hiY);
        entity.setLabel(zoneLabel);
        zoneMapper.insert(entity);

        List<PauseZone> zones = fromDatabase(resolved);
        writeCache(redisProvider.getIfAvailable(), resolved, zones);
        return new PauseZone(loX, loY, hiX, hiY, zoneLabel);
    }

    @Override
    public void clearPauseZones(Long parkId) {
        Long resolved = parkId != null ? parkId : 0L;
        zoneMapper.delete(new QueryWrapper<TrafficPauseZoneEntity>().eq("park_id", resolved));
        writeCache(redisProvider.getIfAvailable(), resolved, List.of());
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

    private List<PauseZone> fromDatabase(Long parkId) {
        List<TrafficPauseZoneEntity> rows = zoneMapper.selectList(
                new QueryWrapper<TrafficPauseZoneEntity>().eq("park_id", parkId).orderByAsc("id"));
        List<PauseZone> zones = new ArrayList<>(rows.size());
        for (TrafficPauseZoneEntity row : rows) {
            zones.add(new PauseZone(row.getMinX(), row.getMinY(), row.getMaxX(), row.getMaxY(), row.getLabel()));
        }
        return zones;
    }

    private void writeCache(StringRedisTemplate redis, Long parkId, List<PauseZone> zones) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + parkId, objectMapper.writeValueAsString(zones), cacheTtl);
        // 仍然吞，但要留痕：Redis 抖动不该让"限行区"这条写路径失败（真相在表里，下次读自然回源重建）。
        // 原来 `catch (Exception ignored) {}` 一声不吭，缓存长期坏掉也没人看得见。
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.debug("traffic pause cache write skipped: {}", ex.toString());
        }
    }
}
