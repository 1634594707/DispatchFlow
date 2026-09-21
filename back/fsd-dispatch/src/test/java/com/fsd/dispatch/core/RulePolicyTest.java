package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 打分算式的黄金值断言（§7.1「算式断言先行」）。
 *
 * <p>这些期望值是按迁移前 {@code DispatchVehicleAssignServiceImpl#scoreCandidate} 的原始算式
 * 逐个手算的，包含几处**有意保留**的行为：高峰只削弱 SOC 惩罚不削弱距离、插电奖励要求恰好等于
 * 满电、距离超过衰减长度后奖励被夹到 0、空闲分钟数在未来时不给奖励。
 * 改动其中任何一条都必须同时改这里，并说明理由。
 */
class RulePolicyTest {

    private static final double EPS = 1e-6;

    private final RulePolicy policy = new RulePolicy();

    private static DecisionWeights weights() {
        return new DecisionWeights(1.0D, 0.15D, 80.0D, 0.5D, 30.0D, 100,
                DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR, DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
                DecisionWeights.DEFAULT_PEAK_SOC_DAMPING, DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);
    }

    private static DecisionInput.CandidateState candidate(long id, String code, int soc, double distance,
                                                          boolean pluggedFullStandby, long idleMinutes) {
        return new DecisionInput.CandidateState(
                new DecisionInput.RankedCandidateIdentity(id, code), soc, distance, pluggedFullStandby, idleMinutes);
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
    void plainOrderScoresDistancePlusSocPenalty() {
        RankedCandidate scored = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "ZJF-AV-01", 80, 1200D, false, 0L))));

        assertEquals(1200D, scored.distanceScore(), EPS);
        assertEquals(3D, scored.socScore(), EPS);
        assertEquals(1.0D, scored.priorityFactor(), EPS);
        assertEquals(1203D, scored.totalScore(), EPS);
    }

    @Test
    void priorityMultipliesTheWholeScore() {
        // HIGH 让同一辆车总分变小 -> 更容易被选中；LOW 变大。是乘数不是加分。
        assertEquals(842.1D, only(policy.decide(input("HIGH", false, 1.0D,
                candidate(1L, "V", 80, 1200D, false, 0L)))).totalScore(), EPS);
        assertEquals(1563.9D, only(policy.decide(input("LOW", false, 1.0D,
                candidate(1L, "V", 80, 1200D, false, 0L)))).totalScore(), EPS);
        assertEquals(1203D, only(policy.decide(input("MEDIUM", false, 1.0D,
                candidate(1L, "V", 80, 1200D, false, 0L)))).totalScore(), EPS);
    }

    @Test
    void peakModeDampsSocPenaltyButNotDistance() {
        RankedCandidate scored = only(policy.decide(input(null, true, 0.85D,
                candidate(1L, "V", 80, 1200D, false, 0L))));

        assertEquals(1020D, scored.distanceScore(), EPS);
        assertEquals(2.1D, scored.socScore(), EPS);
        assertEquals(1022.1D, scored.totalScore(), EPS);
    }

    @Test
    void pluggedStandbyBonusIsSubtractedAndFalloffClamped() {
        RankedCandidate near = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 100D, true, 0L))));
        assertEquals(100D, near.distanceScore(), EPS);
        assertEquals(0D, near.socScore(), EPS);
        assertEquals(64D, near.pluggedBonus(), EPS);
        assertEquals(36D, near.totalScore(), EPS);

        // 距离超出 500 m 衰减长度后奖励夹到 0，不会变成负奖励（即不会反噬成惩罚）
        RankedCandidate far = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 600D, true, 0L))));
        assertEquals(0D, far.pluggedBonus(), EPS);
        assertEquals(600D, far.totalScore(), EPS);
    }

    @Test
    void pluggedBonusRequiresExactlyFullSoc() {
        // 99% 插电驻车不给奖励 —— 条件写的是 soc == fullSoc。属于可疑行为，先钉住再说。
        RankedCandidate scored = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 99, 100D, true, 0L))));
        assertEquals(0D, scored.pluggedBonus(), EPS);
        assertEquals(100.15D, scored.totalScore(), EPS);
    }

    @Test
    void idleBonusAccumulatesPerMinuteAndCapsAtMaxIdleBonus() {
        assertEquals(30D, only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 200D, false, 100L)))).idleBonus(), EPS);
        assertEquals(30D, only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 200D, false, 1000L)))).idleBonus(), EPS);
        assertEquals(170D, only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 200D, false, 100L)))).totalScore(), EPS);

        // 上报时间在未来 -> 负空闲 -> 不奖励也不加分
        assertEquals(0D, only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "V", 100, 200D, false, -5L)))).idleBonus(), EPS);
    }

    @Test
    void ranksAscendingBecauseLowerScoreWins() {
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D,
                candidate(1L, "FAR", 60, 3000D, false, 0L),
                candidate(2L, "NEAR", 90, 200D, false, 0L),
                candidate(3L, "MIDDLE", 75, 1200D, false, 0L)));

        assertEquals(List.of("NEAR", "MIDDLE", "FAR"),
                outcome.ranked().stream().map(RankedCandidate::vehicleCode).toList());
        assertEquals("RULE", outcome.policyId());
        assertEquals("rule-v1", outcome.policyVersion());
    }

    /** §7.2「并列裁决没有显式规则」：并列不再由候选数组的来路顺序（= DB 返回顺序）决定。 */
    @Test
    void tiesAreBrokenByVehicleCodeNotByInputOrder() {
        // 同 SOC、同距离、同空闲 ⇒ 分数逐位相同（M 档实测的 tie_count=2 / score_gap=0.0000 就是这个形状）
        DecisionInput tied = input(null, false, 1.0D,
                candidate(9L, "ZJF-AV-09", 80, 1200D, false, 0L),
                candidate(2L, "ZJF-AV-02", 80, 1200D, false, 0L));

        DecisionOutcome forward = policy.decide(tied);
        DecisionOutcome reversed = policy.decide(input(null, false, 1.0D,
                candidate(2L, "ZJF-AV-02", 80, 1200D, false, 0L),
                candidate(9L, "ZJF-AV-09", 80, 1200D, false, 0L)));

        assertEquals(forward.ranked().get(0).identity().vehicleCode(),
                reversed.ranked().get(0).identity().vehicleCode(), "并列赢家不能随候选数组顺序变");
        assertEquals("ZJF-AV-02", forward.ranked().get(0).identity().vehicleCode(),
                "并列时必须取车号字典序最小那个（显式规则，不是碰巧）");
        assertEquals(0D, forward.ranked().get(0).totalScore() - forward.ranked().get(1).totalScore(), 1e-12,
                "这两条本应是真并列");
    }

    @Test
    void secondaryKeyMustNotOverrideAScoreGap() {
        // 车号更小的那台分数更差 ⇒ 仍必须输给车号更大但分数更好的，次级键不能篡位
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D,
                candidate(1L, "ZJF-AV-01", 80, 1200D, false, 0L),
                candidate(2L, "ZJF-AV-99", 80, 300D, false, 0L)));

        assertEquals("ZJF-AV-99", outcome.ranked().get(0).identity().vehicleCode());
        assertTrue(outcome.ranked().get(0).totalScore() < outcome.ranked().get(1).totalScore());
    }

    @Test
    void missingVehicleCodeFallsBackToVehicleIdInsteadOfThrowing() {
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D,
                candidate(7L, null, 80, 1200D, false, 0L),
                candidate(3L, null, 80, 1200D, false, 0L),
                candidate(5L, "ZJF-AV-05", 80, 1200D, false, 0L)));

        // 无代码的两台按 id 升序排，且空代码排在有代码之后（脏数据不该抢赢正常车）
        assertEquals("ZJF-AV-05", outcome.ranked().get(0).identity().vehicleCode());
        assertEquals(3L, outcome.ranked().get(1).identity().vehicleId());
        assertEquals(7L, outcome.ranked().get(2).identity().vehicleId());
    }

    @Test
    void emptyCandidateSetYieldsEmptyOutcomeInsteadOfThrowing() {
        DecisionOutcome outcome = policy.decide(input(null, false, 1.0D));

        assertTrue(outcome.isEmpty());
    }

    @Test
    void explanationTextKeepsTheResponseContractVerbatim() {
        RankedCandidate best = only(policy.decide(input(null, false, 1.0D,
                candidate(1L, "ZJF-AV-07", 80, 1200D, false, 0L))));

        assertEquals("Selected ZJF-AV-07: distance=1200.0, socPenalty=3.0, pluggedBonus=0.0, "
                        + "idleBonus=0.0, priorityFactor=1.00, total=1203.0",
                RulePolicy.explain(best, false, false));
        assertEquals(", geoBlend=on, mapf=on",
                RulePolicy.explain(best, true, true).substring(RulePolicy.explain(best, false, false).length()));
    }
}
