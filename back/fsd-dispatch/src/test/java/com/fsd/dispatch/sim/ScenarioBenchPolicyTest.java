package com.fsd.dispatch.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.DecisionOutcome;
import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.ForecastAwarePolicy;
import com.fsd.dispatch.core.RulePolicy;
import com.fsd.dispatch.sim.ScenarioBench.Config;
import com.fsd.dispatch.sim.ScenarioBench.PairedSummary;
import com.fsd.dispatch.sim.ScenarioBench.RunResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-1 实验闭环的验收闸门（面试路线图）：同输入连跑两次逐字段全等、UNIFORM 需求下 FORECAST
 * 构造性等于 RULE、灰度分侧稳定且边界正确、挑战者抛错自动回退、regret 在位标尺非负、
 * 恶化告警判据该响就响该静就静。
 */
class ScenarioBenchPolicyTest {

    private static final ForecastAwarePolicy FORECAST = new ForecastAwarePolicy(100D);

    /** 站点需求（skew=1.0）让压力项真正有非零输入；UNIFORM 场景在无压力等价测试里单独建。 */
    private static Config stationCfg(long seed) {
        return Config.mTier(seed).withStationDemand(1.0D);
    }

    @Test
    @DisplayName("灰度世界同一输入连跑两次，RunResult 逐字段全等")
    void grayRunIsFullyDeterministic() {
        Config c = stationCfg(20260921L);
        RunResult a = ScenarioBench.run(c, c.seed() * 31, FORECAST, 25);
        RunResult b = ScenarioBench.run(c, c.seed() * 31, FORECAST, 25);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("UNIFORM 需求压力恒 0 ⇒ FORECAST 逐位等于 RULE（构造保证的回退基线）")
    void forecastEqualsRuleWhenNoPressure() {
        Config c = Config.mTier(20260921L);
        RunResult rule = ScenarioBench.run(c, c.seed() * 31);
        RunResult forecast = ScenarioBench.run(c, c.seed() * 31, FORECAST);
        assertEquals(rule.completed(), forecast.completed());
        assertEquals(rule.totalDistanceMeters(), forecast.totalDistanceMeters(), 0D);
        assertEquals(rule.pickupWaitMeanSeconds(), forecast.pickupWaitMeanSeconds(), 0D);
        assertTrue(forecast.decisionCompared() > 0, "PRIMARY 臂必须逐单做对照");
        assertEquals(forecast.decisionCompared(), forecast.decisionAgreed(), "压力全 0 时 top-1 必须全部一致");
        assertEquals(0D, forecast.regretMean(), 0D);
    }

    @Test
    @DisplayName("灰度边界：0% 全在位、100% 全挑战者")
    void grayBoundariesRouteEverythingToOneSide() {
        Config c = stationCfg(7L);
        RunResult zero = ScenarioBench.run(c, c.seed() * 31, FORECAST, 0);
        assertEquals(0, zero.challengerSide().served());
        assertEquals(0, zero.decisionCompared());
        assertTrue(zero.ruleSide().served() > 0);

        RunResult full = ScenarioBench.run(c, c.seed() * 31, FORECAST, 100);
        assertEquals(0, full.ruleSide().served());
        assertTrue(full.challengerSide().served() > 0);
    }

    @Test
    @DisplayName("同一单重放落同一侧：分侧计数跨 run 可复现，两侧都有单")
    void graySidesAreStableAcrossReplays() {
        Config c = stationCfg(42L);
        RunResult a = ScenarioBench.run(c, c.seed() * 31, FORECAST, 25);
        RunResult b = ScenarioBench.run(c, c.seed() * 31, FORECAST, 25);
        assertEquals(a.ruleSide().served(), b.ruleSide().served());
        assertEquals(a.challengerSide().served(), b.challengerSide().served());
        assertTrue(a.ruleSide().served() > 0);
        assertTrue(a.challengerSide().served() > 0);
    }

    @Test
    @DisplayName("站点需求 + 强压力 ⇒ FORECAST 必须真的改判（压力若按订单取值会退化为常数、永远等于 RULE）")
    void forecastDivergesFromRuleUnderPressure() {
        Config c = stationCfg(42L);
        ForecastAwarePolicy strong = new ForecastAwarePolicy(1000D);
        RunResult r = ScenarioBench.run(c, c.seed() * 31, strong);
        assertTrue(r.decisionCompared() > 0, "PRIMARY 臂必须逐单对照");
        assertTrue(r.decisionAgreed() < r.decisionCompared(),
                "压力按每台候选所属站区取值时，高压力站区的车必须被惩罚改判");
        assertTrue(r.regretMean() > 0D, "改判后的选择按在位标尺必有正 regret");
    }

    @Test
    @DisplayName("UNIFORM 需求无引力事实 ⇒ GRAVITY 逐位等于 RULE（世界级失败回退）")
    void gravityEqualsRuleWhenNoStations() {
        Config c = Config.mTier(20260921L);
        RunResult rule = ScenarioBench.run(c, c.seed() * 31);
        RunResult gravity = ScenarioBench.run(c, c.seed() * 31, new com.fsd.dispatch.core.GravityPolicy());
        assertEquals(rule.completed(), gravity.completed());
        assertEquals(rule.totalDistanceMeters(), gravity.totalDistanceMeters(), 0D);
        assertTrue(gravity.decisionCompared() > 0);
        assertEquals(gravity.decisionCompared(), gravity.decisionAgreed());
        assertEquals(0D, gravity.regretMean(), 0D);
    }

    @Test
    @DisplayName("站点需求下 GRAVITY 必须真的改判（拉离惩罚按供给取值有真实信号）")
    void gravityDivergesWithStationDemand() {
        Config c = stationCfg(42L);
        RunResult r = ScenarioBench.run(c, c.seed() * 31, new com.fsd.dispatch.core.GravityPolicy(500D, 50D));
        assertTrue(r.decisionCompared() > 0);
        assertTrue(r.decisionAgreed() < r.decisionCompared(),
                "拉离惩罚按供给取值时，紧张站区的车必须被改判");
        assertTrue(r.regretMean() > 0D);
    }

    @Test
    @DisplayName("挑战者抛错：自动回落在位，本单不丢，回退计数可见且不进对照")
    void challengerFailureFallsBackToIncumbent() {
        Config c = stationCfg(20260921L);
        RunResult baseline = ScenarioBench.run(c, c.seed() * 31);
        RunResult exploded = ScenarioBench.run(c, c.seed() * 31, new ExplodingPolicy(), 100);
        assertEquals(baseline.completed(), exploded.completed(), "回退后完成量必须与纯在位基线一致");
        assertEquals(baseline.totalDistanceMeters(), exploded.totalDistanceMeters(), 0D);
        assertTrue(exploded.challengerFallbacks() > 0);
        assertEquals(0, exploded.decisionCompared(), "崩掉的决策没有共同标尺，不进对照分母");
        assertEquals(0, exploded.challengerSide().served());
        assertEquals(baseline.completed(), exploded.ruleSide().served());
    }

    @Test
    @DisplayName("挑战者改变选择：agreement < compared，regret 按在位标尺非负且 max ≥ mean")
    void disagreementYieldsNonNegativeRegret() {
        Config c = stationCfg(42L);
        RunResult r = ScenarioBench.run(c, c.seed() * 31, new ReversePolicy(), 100);
        assertTrue(r.decisionCompared() > 0);
        assertTrue(r.decisionAgreed() < r.decisionCompared(), "故意选最差候选，agreement 必须小于全量对照");
        assertTrue(r.regretMean() >= 0D);
        assertTrue(r.regretMax() >= r.regretMean());
    }

    @Test
    @DisplayName("恶化告警判据：完成率掉 >5pp 或等待 P95 恶化 >20% 且 CI 不跨 0 才响")
    void regressionRulesFireAndStayQuiet() {
        List<PairedSummary> bad = List.of(
                new PairedSummary("side_service_rate", 0.90, 0.80, -0.10, -0.12, -0.08, 12),
                new PairedSummary("side_wait_p95_s", 100D, 130D, 30D, 10D, 50D, 12));
        List<String> warnings = ScenarioBench.regressionWarnings(25, 0L, 100, bad);
        assertEquals(2, warnings.size());

        List<PairedSummary> quiet = List.of(
                new PairedSummary("side_service_rate", 0.90, 0.88, -0.02, -0.05, 0.01, 12),
                new PairedSummary("side_wait_p95_s", 100D, 110D, 10D, -5D, 25D, 12));
        assertTrue(ScenarioBench.regressionWarnings(25, 0L, 100, quiet).isEmpty(),
                "差异小或 CI 跨 0 都不许告警");

        List<String> fallbackOnly = ScenarioBench.regressionWarnings(25, 3L, 100, List.of());
        assertEquals(1, fallbackOnly.size());

        assertEquals(1, ScenarioBench.regressionWarnings(25, 0L, 0, List.of()).size(),
                "挑战者侧 0 单本身就要告警");
    }

    /** 永远抛错的挑战者：验证回退语义。 */
    private static final class ExplodingPolicy implements DecisionPolicy {
        @Override
        public String id() {
            return "EXPLODE";
        }

        @Override
        public String version() {
            return "explode-v1";
        }

        @Override
        public DecisionOutcome decide(DecisionInput input) {
            throw new IllegalStateException("boom");
        }
    }

    /** 在位 RULE 的排序整体倒转：每单都拿"最差"候选，用于制造分歧与正 regret。 */
    private static final class ReversePolicy implements DecisionPolicy {
        private final RulePolicy rule = new RulePolicy();

        @Override
        public String id() {
            return "REVERSE";
        }

        @Override
        public String version() {
            return "reverse-v1";
        }

        @Override
        public DecisionOutcome decide(DecisionInput input) {
            List<com.fsd.dispatch.core.RankedCandidate> ranked =
                    new ArrayList<>(rule.decide(input).ranked());
            Collections.reverse(ranked);
            return new DecisionOutcome(ranked, id(), version());
        }
    }
}
