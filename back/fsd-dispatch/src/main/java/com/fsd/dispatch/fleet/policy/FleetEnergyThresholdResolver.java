package com.fsd.dispatch.fleet.policy;

import com.fsd.dispatch.config.FleetEnergyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
 *   <li>{@code fsd:config:energy:low-soc-threshold}（P1-2 起并档）</li>
 * </ul>
 *
 * <p>设计：本地缓存 5 秒，避免高频调度场景下对 Redis 的轮询压力。
 * 缓存按 key 维度独立过期，未命中的 key 回退到 YAML 配置。
 * P1-2 起每次解析记录<b>来源（REDIS/YAML）与生效时间</b>并在值变化时打日志——
 * "现在到底是哪个值在生效"不再靠猜（{@link #describe()}）。
 */
@Component
public class FleetEnergyThresholdResolver {

    private static final Logger log = LoggerFactory.getLogger(FleetEnergyThresholdResolver.class);

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "fsd:config:energy:";

    static final String KEY_RETURN_TO_CHARGE = KEY_PREFIX + "return-to-charge-threshold";
    static final String KEY_MIN_ASSIGNABLE_SOC = KEY_PREFIX + "min-assignable-soc";
    static final String KEY_CHARGE_COMPLETE_SOC = KEY_PREFIX + "charge-complete-soc";
    static final String KEY_CRITICAL_SOC = KEY_PREFIX + "critical-soc-threshold";
    static final String KEY_LOW_SOC = KEY_PREFIX + "low-soc-threshold";

    /** 本地缓存 TTL（秒），避免高频调度对 Redis 的轮询压力 */
    private static final long CACHE_TTL_SECONDS = 5;

    /** SOC 上下限约束 */
    private static final int SOC_MIN = 0;
    private static final int SOC_MAX = 100;

    /** 阈值来源（P1-2 可观测面）：REDIS = Redis 覆盖生效；YAML = 回退配置值。 */
    public record ThresholdSource(String key, int value, String source, LocalDateTime resolvedAt) {
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final FleetEnergyProperties fleetEnergyProperties;

    /** 本地缓存：key → (value, expireAt) */
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 来源记录：key → 最近一次解析的值/来源/时间 */
    private final ConcurrentMap<String, ThresholdSource> sources = new ConcurrentHashMap<>();

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

    /** 低电量展示/告警阈值（%）——P1-2 起与其它四档同走热更新通道 */
    public int getLowSocThreshold() {
        return resolve(KEY_LOW_SOC, fleetEnergyProperties.getLowSocThreshold());
    }

    /** 全部阈值的当前值、来源与生效时间。读一次解析一次（5 秒缓存内命中，无额外 Redis 压力）。 */
    public java.util.List<ThresholdSource> describe() {
        getReturnToChargeThreshold();
        getMinAssignableSoc();
        getChargeCompleteSoc();
        getCriticalSocThreshold();
        getLowSocThreshold();
        return java.util.List.copyOf(sources.values());
    }

    /**
     * 解析阈值：优先 Redis，回退 YAML 默认值。
     * 本地缓存 5 秒以减少 Redis 调用；值变化时打一条日志（P1-2：热更新必须可观测）。
     */
    private int resolve(String redisKey, int fallback) {
        CacheEntry cached = cache.get(redisKey);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt.isAfter(now)) {
            return cached.value;
        }

        int resolved = fallback;
        String source = "YAML";
        try {
            if (stringRedisTemplate != null) {
                String raw = stringRedisTemplate.opsForValue().get(redisKey);
                if (raw != null) {
                    int parsed = Integer.parseInt(raw.trim());
                    if (parsed >= SOC_MIN && parsed <= SOC_MAX) {
                        resolved = parsed;
                        source = "REDIS";
                    }
                }
            }
        } catch (NumberFormatException ignored) {
            // Redis 值格式不合法，回退 YAML 默认值
        } catch (Exception ignored) {
            // Redis 不可用等异常，回退 YAML 默认值
        }

        ThresholdSource previous = sources.get(redisKey);
        if (previous == null || previous.value() != resolved) {
            log.info("energy threshold {} = {} (source={}, previous={})", redisKey, resolved, source,
                    previous == null ? "n/a" : previous.value() + "@" + previous.source());
        }
        sources.put(redisKey, new ThresholdSource(redisKey, resolved, source, LocalDateTime.now()));
        cache.put(redisKey, new CacheEntry(resolved, now.plus(Duration.ofSeconds(CACHE_TTL_SECONDS))));
        return resolved;
    }

    /** 强制刷新缓存（用于测试或运维触发立即生效） */
    public void refresh() {
        cache.clear();
    }

    private record CacheEntry(int value, Instant expireAt) {}
}
