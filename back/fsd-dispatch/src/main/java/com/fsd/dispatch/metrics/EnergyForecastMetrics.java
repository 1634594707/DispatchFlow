package com.fsd.dispatch.metrics;

import com.fsd.dispatch.service.EnergyForecastService.ForecastAvailability;
import com.fsd.dispatch.service.EnergyForecastService.ForecastStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把"预测不可用 → 错峰返充静默回退"变成可观测事件（路线图 M4 第 3 条）。
 *
 * <p>在此之前，日作业 {@code scripts/ml/refresh_energy_forecast.py} 有没有跑，只能靠人去查
 * {@code t_energy_forecast} 的行数：{@code parkPressure} 对"没数据""已超期""确实空闲"一律返回 0，
 * 而 {@code shouldDeferReturnToCharge} 拿到 0 就安静地按纯阈值策略走。行为不变是对的
 * （安全侧不该动），但**运维上看不见**——预测可以连续几周失效而没有任何信号。
 *
 * <p>两条出口，各自负责不同的消费面：
 * <ul>
 *   <li>计数器 {@code dispatchflow.energy_forecast.availability{park,state}}：
 *       每次可用性解析都记一次。持续 {@code state!="FRESH"} 即日作业失效，可被
 *       {@code rate(...)} 阈值告警捕获。计数器随进程重启归零，所以判定口径是<b>速率</b>
 *       而不是总量。</li>
 *   <li>限速 WARN 日志：同一 (park, state) 最多 2 分钟一条，内容即 {@link ForecastStatus#explain()}。
 *       限速是因为解析发生在补能规则评估循环里，逐车逐规则打日志会淹没其他告警。</li>
 * </ul>
 *
 * <p>基数有界：状态枚举 5 个 × 园区数个，日志键同样有界。
 */
@Component
public class EnergyForecastMetrics {

    private static final Logger log = LoggerFactory.getLogger(EnergyForecastMetrics.class);

    static final String AVAILABILITY_METRIC = "dispatchflow.energy_forecast.availability";

    static final String FLAT_PROFILE_METRIC = "dispatchflow.energy_forecast.flat_profile";

    private static final Duration WARN_INTERVAL = Duration.ofMinutes(2);

    private final MeterRegistry registry;
    private final Map<String, Instant> lastWarnAt = new ConcurrentHashMap<>();

    public EnergyForecastMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次可用性解析。
     *
     * <p>{@link ForecastAvailability#FRESH} 只进计数器不打日志：预测正常时它是高频调用点。
     *
     * @return 本次是否真的产出了 WARN（供调用方与测试判断"这条退化有没有被看到"）
     */
    public boolean observe(Long parkId, ForecastStatus status) {
        registry.counter(AVAILABILITY_METRIC,
                "park", String.valueOf(parkId),
                "state", status.availability().name()).increment();

        if (status.usable()) {
            return false;
        }
        if (!shouldWarn(parkId + "#" + status.availability())) {
            return false;
        }
        log.warn("补能预测不可用，错峰返充已回退纯阈值策略：park={} {}", parkId, status.explain());
        return true;
    }

    /**
     * 记录"绝对阈值已过、但当日剖面太平"这一档。
     *
     * <p>它既不是高峰也不是缺数据，而是<b>判据本身失去区分力</b>：压测夹具产出的剖面是
     * 每小时约 150 次到站的平地（峰谷比 1.08），任何绝对阈值都对它恒成立。这一档必须单独存在，
     * 否则"没有触发推迟"会被读成"错峰策略在工作"。
     *
     * @return 本次是否真的产出了 WARN
     */
    public boolean observeFlatProfile(Long parkId, ForecastStatus status) {
        registry.counter(FLAT_PROFILE_METRIC, "park", String.valueOf(parkId)).increment();
        if (!shouldWarn(parkId + "#FLAT_PROFILE")) {
            return false;
        }
        log.warn("补能高峰判据不成立：park={} 压力 {} 已过绝对阈值，但仅为当日逐小时中位的 {}x，"
                        + "低于 min-peak-pressure-ratio —— 剖面太平，按无高峰处理（不推迟返充）。{}",
                parkId, String.format("%.2f", status.pressure()),
                String.format("%.2f", status.peakRatio()), status.explain());
        return true;
    }

    /** 限速只作用于日志；计数器每次都记，否则 rate() 告警会失真。 */
    private boolean shouldWarn(String key) {
        Instant now = Instant.now();
        Instant previous = lastWarnAt.get(key);
        if (previous != null && Duration.between(previous, now).compareTo(WARN_INTERVAL) < 0) {
            return false;
        }
        lastWarnAt.put(key, now);
        return true;
    }
}
