package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.config.FleetEnergyProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 阶段八 8.4：充电触发阈值 Redis 热更新解析器。
 *
 * <p>优先级：Redis key &gt; {@link FleetEnergyProperties}（YAML/默认值）。
 * 修改 Redis 中对应 key 后，下个调度周期（最长 {@link #CACHE_TTL_SECONDS} 秒）生效，无需重启服务。
 *
 * <p>支持的 Redis Key（值必须为 0-100 的整数百分比字符串）：
 * <ul>
 *   <li>{@code fsd:config:energy:return-to-charge-threshold}</li>
 *   <li>{@code fsd:config:energy:min-assignable-soc}</li>
 *   <li>{@code fsd:config:energy:charge-complete-soc}</li>
 *   <li>{@code fsd:config:energy:critical-soc-threshold}</li>
 * </ul>
 *
 * <p>设计：本地缓存 5 秒，避免高频调度场景下对 Redis 的轮询压力。
 * 缓存按 key 维度独立过期，未命中的 key 回退到 YAML 配置。
 */
@Component
public class FleetEnergyThresholdResolver {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "fsd:config:energy:";

    static final String KEY_RETURN_TO_CHARGE = KEY_PREFIX + "return-to-charge-threshold";
    static final String KEY_MIN_ASSIGNABLE_SOC = KEY_PREFIX + "min-assignable-soc";
    static final String KEY_CHARGE_COMPLETE_SOC = KEY_PREFIX + "charge-complete-soc";
    static final String KEY_CRITICAL_SOC = KEY_PREFIX + "critical-soc-threshold";

    /** 本地缓存 TTL（秒），避免高频调度对 Redis 的轮询压力 */
    private static final long CACHE_TTL_SECONDS = 5;

    /** SOC 上下限约束 */
    private static final int SOC_MIN = 0;
    private static final int SOC_MAX = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final FleetEnergyProperties fleetEnergyProperties;

    /** 本地缓存：key → (value, expireAt) */
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FleetEnergyThresholdResolver(StringRedisTemplate stringRedisTemplate,
                                        FleetEnergyProperties fleetEnergyProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.fleetEnergyProperties = fleetEnergyProperties;
    }

    /** 自动回充触发阈值（%） */
    public int getReturnToChargeThreshold() {
        return resolve(KEY_RETURN_TO_CHARGE, fleetEnergyProperties.getReturnToChargeThreshold());
    }

    /** 最低可派单 SOC（%） */
    public int getMinAssignableSoc() {
        return resolve(KEY_MIN_ASSIGNABLE_SOC, fleetEnergyProperties.getMinAssignableSoc());
    }

    /** 充电完成并恢复派单阈值（%） */
    public int getChargeCompleteSoc() {
        return resolve(KEY_CHARGE_COMPLETE_SOC, fleetEnergyProperties.getChargeCompleteSoc());
    }

    /** 危急电量驻车阈值（%） */
    public int getCriticalSocThreshold() {
        return resolve(KEY_CRITICAL_SOC, fleetEnergyProperties.getCriticalSocThreshold());
    }

    /**
     * 解析阈值：优先 Redis，回退 YAML 默认值。
     * 本地缓存 5 秒以减少 Redis 调用。
     */
    private int resolve(String redisKey, int fallback) {
        CacheEntry cached = cache.get(redisKey);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt.isAfter(now)) {
            return cached.value;
        }

        int resolved = fallback;
        try {
            String raw = stringRedisTemplate.opsForValue().get(redisKey);
            if (raw != null) {
                int parsed = Integer.parseInt(raw.trim());
                if (parsed >= SOC_MIN && parsed <= SOC_MAX) {
                    resolved = parsed;
                }
            }
        } catch (NumberFormatException ignored) {
            // Redis 值格式不合法，回退 YAML 默认值
        } catch (Exception ignored) {
            // Redis 不可用等异常，回退 YAML 默认值
        }

        cache.put(redisKey, new CacheEntry(resolved, now.plus(Duration.ofSeconds(CACHE_TTL_SECONDS))));
        return resolved;
    }

    /** 强制刷新缓存（用于测试或运维触发立即生效） */
    public void refresh() {
        cache.clear();
    }

    private record CacheEntry(int value, Instant expireAt) {}
}
