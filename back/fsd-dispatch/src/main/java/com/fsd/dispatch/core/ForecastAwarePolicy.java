package com.fsd.dispatch.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预测感知策略（§2.1 实现 B）：在 {@link RulePolicy} 之上叠加 {@code t_energy_forecast} 的站点压力项。
 *
 * <p>做法是<b>委托</b>而非复制：先拿 {@link RulePolicy} 的分项结果，再按每台车目标站点的预测压力加一个
 * 惩罚项重排。这样两条性质是构造保证的，而不是靠两处代码同步：
 * <ul>
 *   <li>压力全 0（或权重 0）时逐位等于 {@link RulePolicy} —— 影子对照因此有干净的回退基线；</li>
 *   <li>并列裁决沿用 {@link RulePolicy#rankOrder()}，不另起一套次级键。</li>
 * </ul>
 *
 * <p>惩罚项<b>不乘</b>优先级系数：站点压力是这次派单本身的绝对成本（会把车导向一个即将饱和的点），
 * 与"这单多急"无关；若一起乘上去，HIGH 单反而会削弱本该遵守的错峰约束。所以
 * {@code total = ruleTotal + forecastPressure * pressureWeight}。
 *
 * <p>这是 {@code com.fsd.dispatch.core} 里的纯函数，不碰数据库/Spring/时钟；压力值由调用方在热路径
 * 解析进 {@link DecisionInput.CandidateState#forecastPressure()}。§2.2 的晋级尚未接线前，热路径传 0，
 * 本类只在影子/离线实验里被显式实例化，<b>不注册为 {@code decisionPolicy} 主 bean</b>。
 */
public final class ForecastAwarePolicy implements DecisionPolicy {

    public static final String POLICY_ID = "FORECAST";
    public static final String POLICY_VERSION = "forecast-v1";

    /**
     * 默认压力权重。距离权重是 1.0/米，故 pressure=1.0 的惩罚 ≈ 100 米等效绕行——
     * 一个"值得为错峰多绕一点、但不至于压过真实可达性"的量级。可在构造时覆盖，供实验台扫描。
     */
    public static final double DEFAULT_PRESSURE_WEIGHT = 100.0D;

    private final RulePolicy rulePolicy;
    private final double pressureWeight;

    public ForecastAwarePolicy() {
        this(DEFAULT_PRESSURE_WEIGHT);
    }

    public ForecastAwarePolicy(double pressureWeight) {
        this(new RulePolicy(), pressureWeight);
    }

    ForecastAwarePolicy(RulePolicy rulePolicy, double pressureWeight) {
        this.rulePolicy = rulePolicy;
        this.pressureWeight = pressureWeight;
    }

    @Override
    public String id() {
        return POLICY_ID;
    }

    @Override
    public String version() {
        return POLICY_VERSION;
    }

    @Override
    public DecisionOutcome decide(DecisionInput input) {
        DecisionOutcome base = rulePolicy.decide(input);
        if (base.isEmpty() || pressureWeight == 0D) {
            return new DecisionOutcome(base.ranked(), POLICY_ID, POLICY_VERSION);
        }

        Map<DecisionInput.RankedCandidateIdentity, Double> pressureByIdentity = new HashMap<>();
        for (DecisionInput.CandidateState candidate : input.candidates()) {
            pressureByIdentity.put(candidate.identity(), candidate.forecastPressure());
        }

        List<RankedCandidate> adjusted = new ArrayList<>(base.ranked().size());
        for (RankedCandidate ranked : base.ranked()) {
            double pressure = pressureByIdentity.getOrDefault(ranked.identity(), 0D);
            double forecastPenalty = pressure * pressureWeight;
            adjusted.add(new RankedCandidate(ranked.identity(),
                    ranked.distanceScore(), ranked.socScore(), ranked.pluggedBonus(),
                    ranked.idleBonus(), ranked.priorityFactor(), forecastPenalty,
                    ranked.totalScore() + forecastPenalty));
        }
        adjusted.sort(RulePolicy.rankOrder());
        return new DecisionOutcome(adjusted, POLICY_ID, POLICY_VERSION);
    }
}
