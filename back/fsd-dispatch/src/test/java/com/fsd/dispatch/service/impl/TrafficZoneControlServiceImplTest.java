package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.entity.TrafficPauseZoneEntity;
import com.fsd.dispatch.mapper.TrafficPauseZoneMapper;
import com.fsd.dispatch.service.TrafficZoneControlService.PauseZone;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * §7.4 cache-aside 语义：真相在 MySQL，Redis 只是带 TTL 的缓存。
 *
 * <p>钉住的三条不变式：命中缓存不查库；未命中回源并<b>带 TTL</b> 回填；无 Redis 时直接读库（不因缺缓存退化）。
 */
class TrafficZoneControlServiceImplTest {

    private static final long TTL_SECONDS = 900L;
    private static final String KEY = "fsd:traffic:pause:1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectProvider<StringRedisTemplate> redisProvider;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private TrafficPauseZoneMapper mapper;
    private TrafficZoneControlServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        mapper = org.mockito.Mockito.mock(TrafficPauseZoneMapper.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        service = new TrafficZoneControlServiceImpl(redisProvider, objectMapper, mapper, TTL_SECONDS);
    }

    private static TrafficPauseZoneEntity row(long id, double minX, double minY, double maxX, double maxY) {
        TrafficPauseZoneEntity e = new TrafficPauseZoneEntity();
        e.setId(id);
        e.setParkId(1L);
        e.setMinX(minX);
        e.setMinY(minY);
        e.setMaxX(maxX);
        e.setMaxY(maxY);
        e.setLabel("管制区");
        return e;
    }

    @Test
    void cacheHitDoesNotTouchDatabase() {
        String cached = "[{\"minX\":0.0,\"minY\":0.0,\"maxX\":10.0,\"maxY\":10.0,\"label\":\"管制区\"}]";
        when(valueOps.get(KEY)).thenReturn(cached);

        List<PauseZone> zones = service.listPauseZones(1L);

        assertEquals(1, zones.size());
        assertEquals(10.0, zones.get(0).maxX(), 1e-9);
        verify(mapper, never()).selectList(any());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void cacheMissReadsTruthAndRefillsWithTtl() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of(row(1L, 0D, 0D, 10D, 10D)));

        List<PauseZone> zones = service.listPauseZones(1L);

        assertEquals(1, zones.size());
        verify(mapper).selectList(any());
        // 回填必须带 TTL（§7.4「Redis 必须有 TTL 且可从 MySQL 重建」）
        verify(valueOps).set(eq(KEY), anyString(), eq(Duration.ofSeconds(TTL_SECONDS)));
    }

    @Test
    void worksWithoutRedisByReadingTruthDirectly() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of(row(1L, 0D, 0D, 5D, 5D)));

        List<PauseZone> zones = service.listPauseZones(1L);

        assertEquals(1, zones.size());
        verify(mapper).selectList(any());
    }

    @Test
    void addPersistsToTruthThenRefreshesCache() {
        when(valueOps.get(KEY)).thenReturn("[]");
        when(mapper.selectList(any())).thenReturn(List.of(row(1L, 1D, 2D, 3D, 4D)));

        PauseZone added = service.addPauseZone(1L, 3D, 4D, 1D, 2D, null);

        // 归一化 min/max：传反的角也要摆正；label 缺省补「管制区」
        assertEquals(new PauseZone(1D, 2D, 3D, 4D, "管制区"), added);
        verify(mapper).insert(any(TrafficPauseZoneEntity.class));
        verify(valueOps).set(eq(KEY), anyString(), eq(Duration.ofSeconds(TTL_SECONDS)));
    }

    @Test
    void clearDeletesTruthAndWritesEmptyCache() {
        service.clearPauseZones(1L);

        verify(mapper).delete(any());
        verify(valueOps).set(eq(KEY), eq("[]"), eq(Duration.ofSeconds(TTL_SECONDS)));
    }

    @Test
    void pointInsideCachedZoneIsPausedAndOutsideIsNot() {
        when(valueOps.get(KEY)).thenReturn("[{\"minX\":0.0,\"minY\":0.0,\"maxX\":10.0,\"maxY\":10.0,\"label\":\"管制区\"}]");

        assertTrue(service.isPointInPausedZone(1L, new BigDecimal("5"), new BigDecimal("5")));
        assertFalse(service.isPointInPausedZone(1L, new BigDecimal("11"), new BigDecimal("5")));
        assertFalse(service.isPointInPausedZone(1L, null, new BigDecimal("5")));
    }

    @Test
    void nullParkIdNormalisesToZero() {
        when(valueOps.get("fsd:traffic:pause:0")).thenReturn("[]");
        assertEquals(List.of(), service.listPauseZones(null));
    }
}
