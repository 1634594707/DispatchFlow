package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §2.1 实现 B 的算式断言。权重与 {@link RulePolicyTest} 完全一致，好让"委托基线"可直接对照。
 *
 * <p>三类必须钉住的行为：压力全 0 时逐位等于规则策略（回退基线）；压力项按 {@code pressure*weight} 加进总分
 * 且<b>不</b>乘优先级系数；以及压力足以翻转"就近选车"从而体现错峰。
 */
class ForecastAwarePolicyTest {

    private static final double EPS = 1e-6;

    private final ForecastAwarePolicy policy = new ForecastAwarePolicy();
    private final RulePolicy rule = new RulePolicy();

    private static DecisionWeights weights() {
        return new DecisionWeights(1.0D, 0.15D, 80.0D, 0.5D, 30.0D, 100,
                DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR, DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
                DecisionWeights.DEFAULT_PEAK_SOC_DAMPING, DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);
    }

    private static DecisionInput.CandidateState candidate(long id, String code, int soc, double distance,
                                                          boolean pluggedFullStandby, long idleMinutes,
                                                          double forecastPressure) {
        return new DecisionInput.CandidateState(
                new DecisionInput.RankedCandidateIdentity(id, code), soc, distance,
                pluggedFullStandby, idleMinutes, forecastPressure);
    }

    private static DecisionInput input(String priority, boolean peakMode, double peakFactor,
                                       DecisionInput.CandidateState... candidates) {
        return new DecisionInput(priority, peakMode, peakFactor, weights(), List.of(candidates));
    }

    private RankedCandidate only(DecisionOutcome outcome) {
        assertEquals(1, outcome.ranked().size());
        return outcome.ranked().get(0);
    }

    @Test
    void zeroPressureReproducesRulePolicyBitForBit() {
        DecisionInput in = input(null, false, 1.0D,
                candidate(1L, "FAR", 60, 3000D, false, 0L, 0D),
                candidate(2L, "NEAR", 90, 200D, false, 0L, 0D),
                candidate(3L, "MIDDLE", 75, 1200D, false, 0L, 0D));

        DecisionOutcome forecast = policy.decide(in);
        DecisionOutcome baseline = rule.decide(in);

        assertEquals(List.of("NEAR", "MIDDLE", "FAR"),
                forecast.ranked().stream().map(RankedCandidate::vehicleCode).toList());
        for (int i = 0; i < baseline.ranked().size(); i++) {
            assertEquals(baseline.ranked().get(i).totalScore(), forecast.ranked().get(i).totalScore(), EPS);
            assertEquals(0D, forecast.ranked().get(i).forecastPenalty(), EPS);
        }
        assertEquals("FORECAST", forecast.policyId());
        assertEquals("forecast-v1", forecast.policyVersion());
    }

    @Test
    void pressureAddsWeightedPenaltyWithoutTouchingRuleComponents() {
        // 规则分 = 距离 200*1.0 + SOC (100-80)*0.15 = 203；压力 1.0 × 权重 100 = 100 -> 总分 303
        RankedCandidate scored = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 80, 200D, false, 0L, 1.0D))));

        assertEquals(200D, scored.distanceScore(), EPS);
        assertEquals(3D, scored.socScore(), EPS);
        assertEquals(100D, scored.forecastPenalty(), EPS);
        assertEquals(303D, scored.totalScore(), EPS);
    }

    @Test
    void customPressureWeightScalesThePenalty() {
        ForecastAwarePolicy scaled = new ForecastAwarePolicy(50.0D);
        // 同样压力 0.6，权重 50 -> 惩罚 30，规则分 203 -> 总分 233
        RankedCandidate scored = only(scaled.decide(input(null, false, 1.0D,
                candidate(1L, "V", 80, 200D, false, 0L, 0.6D))));

        assertEquals(30D, scored.forecastPenalty(), EPS);
        assertEquals(233D, scored.totalScore(), EPS);
    }

    /** 错峰存在的意义：压力足以让"更近但更堵"的车输给"更远但空闲"的车。 */
    @Test
    void highPressureCanFlipTheNearestVehiclePick() {
        DecisionInput in = input(null, false, 1.0D,
                candidate(1L, "NEAR_SATURATED", 80, 200D, false, 0L, 1.0D),   // 规则 203 + 100 = 303
                candidate(2L, "FAR_FREE", 80, 260D, false, 0L, 0D));          // 规则 263 + 0 = 263

        DecisionOutcome outcome = policy.decide(in);
        assertEquals("FAR_FREE", outcome.ranked().get(0).vehicleCode());
        // 对照：无压力时规则策略会选更近的那台，证明差异确实来自预测项
        assertEquals("NEAR_SATURATED", rule.decide(in).ranked().get(0).vehicleCode());
    }

    /** 惩罚不乘优先级系数：HIGH 单的规则分被乘 0.7，但压力惩罚仍是原始的 100。 */
    @Test
    void pressurePenaltyIsNotScaledByPriorityFactor() {
        RankedCandidate high = only(policy.decide(input("HIGH", false, 1.0D,
                candidate(1L, "V", 80, 200D, false, 0L, 1.0D))));

        // 规则分 (200 + 3) * 0.7 = 142.1，压力惩罚原样 +100
        assertEquals(142.1D, high.distanceScore() * high.priorityFactor()
                + high.socScore() * high.priorityFactor(), EPS);
        assertEquals(100D, high.forecastPenalty(), EPS);
        assertEquals(242.1D, high.totalScore(), EPS);
    }

    /** 并列（同压力、同规则分）仍按 vehicleCode 字典序裁决，不引入新次级键。 */
    @Test
    void tiesStillBreakByVehicleCode() {
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D,
                candidate(9L, "ZJF-AV-09", 80, 200D, false, 0L, 0.4D),
                candidate(2L, "ZJF-AV-02", 80, 200D, false, 0L, 0.4D)));

        assertEquals("ZJF-AV-02", outcome.ranked().get(0).vehicleCode());
        assertEquals(0D, outcome.ranked().get(0).totalScore() - outcome.ranked().get(1).totalScore(), 1e-12);
    }

    @Test
    void emptyCandidateSetYieldsEmptyForecastOutcome() {
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D));
        assertTrue(outcome.isEmpty());
        assertEquals("FORECAST", outcome.policyId());
    }
}
