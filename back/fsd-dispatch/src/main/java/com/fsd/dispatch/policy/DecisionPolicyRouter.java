package com.fsd.dispatch.policy;

import com.fsd.dispatch.config.DispatchPolicyProperties;
import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.DecisionOutcome;
import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.metrics.DispatchDecisionMetrics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 三阶段晋级路由器（路线图 §2.2）。
 *
 * <p>把"用哪个策略"从 {@code DecisionPolicyConfiguration} 里写死的单个 bean 变成**每单解析一次**的显式决定：
 * <ul>
 *   <li>{@code OFF} / {@code SHADOW} → 结果始终由在位策略产出；</li>
 *   <li>{@code GRAY} → 由订单稳定分桶决定落哪一侧，同一单重放必须落同一侧（禁 {@code ThreadLocalRandom}，§7.2）；</li>
 *   <li>{@code PRIMARY} → 挑战者接管。</li>
 * </ul>
 *
 * <p>两条不可让的边界：
 * <ol>
 *   <li><b>SHADOW 不得改变结果</b>。影子只把同一份 {@link DecisionInput} 再喂给挑战者一次，比对 top-1 与 regret
 *       后写进 trace/指标；影子抛错只记一条计数，绝不影响派单。</li>
 *   <li><b>挑战者抛错必须回退</b>。GRAY/PRIMARY 下挑战者异常即回落在位策略并计数 —— §2.2 要求"回退开关一分钟内可切"，
 *       运行期覆盖（{@link #applyOverride}）就是那个开关，改配置 + 重启不算。</li>
 * </ol>
 *
 * <p>regret 的口径刻意保守：用<b>在位策略的标尺</b>量影子选择的分差
 * （{@code primaryScore(shadowWinner) - primaryScore(primaryWinner)}，≥0）。两个策略各自的总分不可直接比大小
 * （{@code ForecastAwarePolicy} 多了一项惩罚），所以不报"谁的分更低"，只报"按现役打分，这一换亏多少"。
 */
@Component
public class DecisionPolicyRouter {

    private static final Logger log = LoggerFactory.getLogger(DecisionPolicyRouter.class);

    private final DecisionPolicy incumbent;
    private final Map<String, DecisionPolicy> candidates;
    private final DispatchPolicyProperties properties;
    private final DispatchDecisionMetrics metrics;
    private final Map<String, DecisionPolicy> guarded = new ConcurrentHashMap<>();

    private volatile String overrideChallenger;
    private volatile DispatchPolicyProperties.Mode overrideMode;
    private volatile Integer overrideGrayPercent;

    public DecisionPolicyRouter(DecisionPolicy decisionPolicy,
                                List<DecisionPolicy> allPolicies,
                                DispatchPolicyProperties properties,
                                DispatchDecisionMetrics metrics) {
        this.incumbent = decisionPolicy;
        this.properties = properties;
        this.metrics = metrics;
        Map<String, DecisionPolicy> byId = new ConcurrentHashMap<>();
        for (DecisionPolicy policy : allPolicies) {
            byId.put(policy.id(), policy);
        }
        byId.putIfAbsent(incumbent.id(), incumbent);
        this.candidates = byId;
    }

    /**
     * 本单生效的策略。分桶键必须是稳定值（订单 id / 订单号），且每单只解析一次并向下传递。
     */
    public DecisionPolicy selectForOrder(String bucketKey) {
        DispatchPolicyProperties.Mode mode = effectiveMode();
        if (mode == DispatchPolicyProperties.Mode.GRAY && bucket(bucketKey) < effectiveGrayPercent()) {
            return challenger();
        }
        if (mode == DispatchPolicyProperties.Mode.PRIMARY) {
            return challenger();
        }
        metrics.recordPolicyRoute(mode.name(), incumbent.id());
        return incumbent;
    }

    /** SHADOW 阶段是否需要跑一次影子对照。GRAY/PRIMARY 下结果本身就是挑战者，不额外记影子。 */
    public boolean shadowActive() {
        return effectiveMode() == DispatchPolicyProperties.Mode.SHADOW;
    }

    /**
     * 影子对照：只在 SHADOW 下调用，只记录、不改变结果。
     *
     * <p>用的是<b>未包回退</b>的挑战者本体：包一层回退的话，挑战者抛错时会拿在位策略的结果冒充影子记录，
     * 快照里就会出现"影子策略 = RULE"这种半真数据 —— 影子要量的是挑战者，不是它的替身。
     *
     * @param incumbentOutcome 在位策略刚产出的结果（同一份 input）
     */
    public void shadowCompare(DecisionInput input, DecisionOutcome incumbentOutcome, DecisionTrace trace) {
        if (!shadowActive() || incumbentOutcome == null) {
            return;
        }
        DecisionPolicy challenger = rawChallenger();
        if (challenger == null) {
            return;
        }
        try {
            DecisionOutcome shadow = challenger.decide(input);
            boolean agreed = sameTop1(incumbentOutcome.ranked(), shadow.ranked());
            Double regret = regretOnIncumbentScale(incumbentOutcome.ranked(), shadow.ranked());
            metrics.recordShadowCompare(challenger.id(), agreed, regret);
            if (trace != null) {
                trace.setShadowPolicyId(shadow.policyId());
                trace.setShadowPolicyVersion(shadow.policyVersion());
                trace.setShadowWinnerCode(topCode(shadow.ranked()));
                trace.setShadowAgreed(agreed);
                trace.setShadowRegret(regret);
            }
        } catch (RuntimeException ex) {
            // 影子失败不得冒泡到派单：这条计数就是"影子策略在真实入参上崩了"的唯一告警面
            metrics.recordShadowFailure(challenger.id());
            log.warn("shadow policy {} failed: {}", challenger.id(), ex.toString());
        }
    }

    /** 管理端运行时切换（§2.2 回退闸门）：传 null 表示该项沿用配置文件值。 */
    public void applyOverride(DispatchPolicyProperties.Mode mode, Integer grayPercent, String challengerId) {
        this.overrideMode = mode;
        this.overrideGrayPercent = grayPercent == null ? null : Math.max(0, Math.min(100, grayPercent));
        this.overrideChallenger = challengerId == null || challengerId.isBlank() ? null : challengerId.trim().toUpperCase(java.util.Locale.ROOT);
        log.warn("decision policy stage switched at runtime: mode={} grayPercent={} challenger={}",
                effectiveMode(), effectiveGrayPercent(), effectiveChallengerId());
    }

    public void clearOverride() {
        this.overrideMode = null;
        this.overrideGrayPercent = null;
        this.overrideChallenger = null;
    }

    public DispatchPolicyProperties.Mode effectiveMode() {
        DispatchPolicyProperties.Mode override = overrideMode;
        return override != null ? override : properties.getMode();
    }

    public int effectiveGrayPercent() {
        Integer override = overrideGrayPercent;
        return override != null ? override : properties.getGrayPercent();
    }

    public String effectiveChallengerId() {
        String override = overrideChallenger;
        return override != null ? override : properties.getChallenger();
    }

    /** 已注册可路由的策略标识，管理端下拉与"这个 challenger 存不存在"的判据都读这里。 */
    public java.util.Set<String> registeredPolicyIds() {
        return java.util.Set.copyOf(candidates.keySet());
    }

    public boolean overrideActive() {
        return overrideMode != null || overrideGrayPercent != null || overrideChallenger != null;
    }

    /** 稳定分桶：同一键恒定落在同一侧，且不用随机数（§7.2 灰度重掷缺陷的根因）。 */
    public static int bucket(String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return 0;
        }
        return Math.floorMod(bucketKey.hashCode(), 100);
    }

    private DecisionPolicy challenger() {
        DecisionPolicy policy = rawChallenger();
        if (policy == null || policy.id().equals(incumbent.id())) {
            return incumbent;
        }
        return guarded.computeIfAbsent(policy.id(), key -> withFallback(policy));
    }

    /** @return 配置的 challenger 本体；未注册时返回 null（调用方决定是回落在位策略还是什么都不做） */
    private DecisionPolicy rawChallenger() {
        String id = effectiveChallengerId();
        DecisionPolicy policy = candidates.get(id);
        if (policy == null) {
            metrics.recordPolicyFallback(id);
            log.warn("challenger policy {} is not registered, staying on {}", id, incumbent.id());
            return null;
        }
        return policy;
    }

    private DecisionPolicy withFallback(DecisionPolicy policy) {
        return new DecisionPolicy() {
            @Override
            public String id() {
                return policy.id();
            }

            @Override
            public String version() {
                return policy.version();
            }

            @Override
            public DecisionOutcome decide(DecisionInput input) {
                try {
                    DecisionOutcome outcome = policy.decide(input);
                    metrics.recordPolicyRoute(effectiveMode().name(), outcome.policyId());
                    return outcome;
                } catch (RuntimeException ex) {
                    metrics.recordPolicyFallback(policy.id());
                    log.warn("policy {} failed, falling back to {}: {}", policy.id(), incumbent.id(), ex.toString());
                    DecisionOutcome fallback = incumbent.decide(input);
                    metrics.recordPolicyRoute("FALLBACK", fallback.policyId());
                    return fallback;
                }
            }
        };
    }

    private static boolean sameTop1(List<RankedCandidate> incumbent, List<RankedCandidate> shadow) {
        Long a = topId(incumbent);
        Long b = topId(shadow);
        return a != null && a.equals(b);
    }

    /** 影子选择按在位策略打分时的分差；影子选择的车主不在位候选清单里 ⇒ 无共同标尺，返回 null 而不是 0。 */
    private static Double regretOnIncumbentScale(List<RankedCandidate> incumbent, List<RankedCandidate> shadow) {
        Long shadowWinner = topId(shadow);
        if (shadowWinner == null) {
            return null;
        }
        Double shadowScoreOnIncumbent = null;
        Double incumbentBest = null;
        for (RankedCandidate candidate : incumbent) {
            if (candidate.vehicleId() == null) {
                continue;
            }
            if (incumbentBest == null) {
                incumbentBest = candidate.totalScore();
            }
            if (candidate.vehicleId().equals(shadowWinner)) {
                shadowScoreOnIncumbent = candidate.totalScore();
            }
        }
        if (shadowScoreOnIncumbent == null || incumbentBest == null) {
            return null;
        }
        return shadowScoreOnIncumbent - incumbentBest;
    }

    private static Long topId(List<RankedCandidate> ranked) {
        return ranked == null || ranked.isEmpty() ? null : ranked.get(0).vehicleId();
    }

    private static String topCode(List<RankedCandidate> ranked) {
        return ranked == null || ranked.isEmpty() ? null : ranked.get(0).vehicleCode();
    }
}
