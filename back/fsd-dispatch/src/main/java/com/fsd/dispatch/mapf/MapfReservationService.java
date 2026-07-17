package com.fsd.dispatch.mapf;

import com.fsd.dispatch.config.MapfProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 时空预约表：segment×timeBucket → vehicleId（M5.1）。
 *
 * <p>ALG-01 fix: in addition to reserving directed edges, the service now also reserves
 * the arrival node for each bucket window. This prevents two vehicles from different
 * directions from arriving at the same intersection node simultaneously.
 */
@Service
public class MapfReservationService {

    private static final String KEY_PREFIX = "mapf:res:";
    private static final String NODE_KEY_PREFIX = "mapf:node:";

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
            // ALG-01 fix: also reserve the arrival node (toNode) for this bucket so that
            // two vehicles converging on the same intersection from different edges cannot
            // occupy the node at the same time. The fromNode is reserved implicitly by the
            // previous iteration (or by the vehicle's prior edge reservation).
            String nodeKey = buildNodeKey(parkId, toNode, bucket);
            Boolean nodeAcquired = stringRedisTemplate.opsForValue().setIfAbsent(nodeKey, vid, ttl);
            if (!Boolean.TRUE.equals(nodeAcquired)) {
                String nodeHolder = stringRedisTemplate.opsForValue().get(nodeKey);
                if (nodeHolder != null && !nodeHolder.equals(vid)) {
                    rollback(acquiredKeys);
                    return false;
                }
            }
            acquiredKeys.add(nodeKey);
        }
        // ALG-01: also reserve the starting node for the very first bucket to prevent
        // another vehicle from jumping onto the same start node while we begin traversing.
        String startNodeKey = buildNodeKey(parkId, fromNode, startBucket);
        Boolean startNodeAcquired = stringRedisTemplate.opsForValue().setIfAbsent(startNodeKey, vid, ttl);
        if (!Boolean.TRUE.equals(startNodeAcquired)) {
            String holder = stringRedisTemplate.opsForValue().get(startNodeKey);
            if (holder != null && !holder.equals(vid)) {
                rollback(acquiredKeys);
                return false;
            }
        } else {
            acquiredKeys.add(startNodeKey);
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

    private static String buildNodeKey(Long parkId, String node, long bucket) {
        return NODE_KEY_PREFIX + parkId + ":" + node + ":" + bucket;
    }
}
