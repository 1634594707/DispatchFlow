package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.core.DecisionInput.CandidateState;
import com.fsd.dispatch.core.DecisionInput.GravityView;
import com.fsd.dispatch.core.DecisionInput.RankedCandidateIdentity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-3 验收闸门（面试路线图）：距离↑引力↓、供给↓压力↑（含 max 地板）、NONE 逐位回退 RULE、
 * 拉离惩罚与目的地引力各自真的改判。硬约束优先是构造口径：候选清单在进策略前已过滤不可达车，
 * 策略层只重排给定候选，测试以"只对给定候选排序"为契约。
 */
class GravityPolicyTest {

    private static RankedCandidateIdentity id(long n) {
        return new RankedCandidateIdentity(n, "V-" + n);
    }

    private static DecisionInput input(CandidateState... states) {
        DecisionWeights weights = new DecisionWeights(1.0D, 0.15D, 80.0D, 0.5D, 30.0D, 100,
                DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR, DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
                DecisionWeights.DEFAULT_PEAK_SOC_DAMPING, DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);
        return new DecisionInput("NORMAL", false, 1.0D, weights, List.of(states));
    }

    @Test
    @DisplayName("闸门：距离增加时吸引力单调下降")
    void attractionDecreasesWithDistance() {
        double prev = Double.POSITIVE_INFINITY;
        for (int metres = 0; metres <= 5000; metres += 100) {
            double a = GravityPolicy.attraction(3D, metres / 1000D, GravityPolicy.DEFAULT_EPSILON_KM);
            assertTrue(a < prev, "吸引力在 r=" + metres + " m 处没有下降");
            prev = a;
        }
    }

    @Test
    @DisplayName("闸门：可用供给减少时压力单调上升，供给 0 视同 1（max 地板）")
    void pressureIncreasesAsSupplyShrinks() {
        double prev = Double.NEGATIVE_INFINITY;
        for (int supply = 20; supply >= 1; supply--) {
            double p = GravityPolicy.stationPressure(3D, 1.0D, supply);
            assertTrue(p > prev, "压力在 supply=" + supply + " 处没有上升");
            prev = p;
        }
        assertEquals(GravityPolicy.stationPressure(3D, 1.0D, 0),
                GravityPolicy.stationPressure(3D, 1.0D, 1), 0D, "供给 0 必须视同 1");
    }

    @Test
    @DisplayName("压力对需求与优先级权重都是线性的")
    void pressureScalesLinearly() {
        assertEquals(GravityPolicy.stationPressure(3D, 1.0D, 1) * 2,
                GravityPolicy.stationPressure(6D, 1.0D, 1), 1e-9);
        assertEquals(GravityPolicy.stationPressure(3D, 2.0D, 4),
                2 * GravityPolicy.stationPressure(3D, 1.0D, 4), 1e-9);
    }

    @Test
    @DisplayName("闸门：无引力事实（NONE）时逐位等于 RULE——构造保证的失败回退")
    void noneGravityViewIsBitwiseRule() {
        CandidateState a = new CandidateState(id(1), 90, 300, false, 0L);
        CandidateState b = new CandidateState(id(2), 70, 900, false, 5L);
        DecisionOutcome rule = new RulePolicy().decide(input(a, b));
        DecisionOutcome gravity = new GravityPolicy().decide(input(a, b));
        assertEquals(rule.ranked().size(), gravity.ranked().size());
        for (int i = 0; i < rule.ranked().size(); i++) {
            assertEquals(rule.ranked().get(i).identity(), gravity.ranked().get(i).identity());
            assertEquals(rule.ranked().get(i).totalScore(), gravity.ranked().get(i).totalScore(), 0D);
        }
    }

    @Test
    @DisplayName("拉离惩罚：同样的距离与 SOC，供给紧张站区的车被排后")
    void pullPenaltyDeprioritizesUnderstaffedStation() {
        // 两台车对 RULE 完全同分（同距离同 SOC 同空闲），只差引力事实；
        // RULE 的并列裁决按 vehicleCode 会选 V-1，GRAVITY 必须翻到 V-2
        CandidateState hot = new CandidateState(id(1), 90, 500, false, 0L, 0D,
                new GravityView(3, 1, 0));
        CandidateState calm = new CandidateState(id(2), 90, 500, false, 0L, 0D,
                new GravityView(0, 5, 0));
        List<RankedCandidate> ranked = new GravityPolicy().decide(input(hot, calm)).ranked();
        assertEquals(Long.valueOf(2L), ranked.get(0).vehicleId(), "紧张站区的车必须被排后");
    }

    @Test
    @DisplayName("目的地引力：同样距离与 SOC，卸货站区需求更高的候选拿到抵扣")
    void attractionRewardsHotDestination() {
        CandidateState hot = new CandidateState(id(1), 90, 500, false, 0L, 0D,
                new GravityView(0, 5, 3));
        CandidateState cold = new CandidateState(id(2), 90, 500, false, 0L, 0D,
                new GravityView(0, 5, 0));
        List<RankedCandidate> ranked = new GravityPolicy().decide(input(hot, cold)).ranked();
        assertEquals(Long.valueOf(1L), ranked.get(0).vehicleId(), "高需求目的地的候选必须被奖励");
    }
}
