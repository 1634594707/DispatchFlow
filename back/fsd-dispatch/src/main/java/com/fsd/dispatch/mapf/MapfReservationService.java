package com.fsd.dispatch.mapf;

import com.fsd.dispatch.config.MapfProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 时空预约表：segment×timeBucket → vehicleId（M5.1）。
 */
@Service
public class MapfReservationService {

    private static final String KEY_PREFIX = "mapf:res:";

    private final MapfProperties mapfProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public MapfReservationService(MapfProperties mapfProperties, StringRedisTemplate stringRedisTemplate) {
        this.mapfProperties = mapfProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isEnabled() {
        return mapfProperties.isEnabled();
    }

    public long currentBucket() {
        long bucketMs = Math.max(100L, mapfProperties.getBucketMs());
        return System.currentTimeMillis() / bucketMs;
    }

    /**
     * 尝试预占一条有向边连续 horizonBuckets 个时间桶；检测对向冲突。
     */
    public boolean tryReserveEdge(Long parkId, Long vehicleId, String fromNode, String toNode, long startBucket) {
        if (!isEnabled() || parkId == null || vehicleId == null || fromNode == null || toNode == null) {
            return true;
        }
        String vid = vehicleId.toString();
        int horizon = Math.max(1, mapfProperties.getHorizonBuckets());
        Duration ttl = Duration.ofMillis(mapfProperties.getBucketMs() * (horizon + 2L));
        List<String> acquiredKeys = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            long bucket = startBucket + i;
            String forwardKey = buildKey(parkId, fromNode, toNode, bucket);
            String reverseKey = buildKey(parkId, toNode, fromNode, bucket);
            String reverseHolder = stringRedisTemplate.opsForValue().get(reverseKey);
            if (reverseHolder != null && !reverseHolder.equals(vid)) {
                rollback(acquiredKeys);
                return false;
            }
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(forwardKey, vid, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                String holder = stringRedisTemplate.opsForValue().get(forwardKey);
                if (holder != null && !holder.equals(vid)) {
                    rollback(acquiredKeys);
                    return false;
                }
            }
            acquiredKeys.add(forwardKey);
        }
        return true;
    }

    private void rollback(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
    }

    private static String buildKey(Long parkId, String from, String to, long bucket) {
        return KEY_PREFIX + parkId + ":" + from + ">" + to + ":" + bucket;
    }
}
