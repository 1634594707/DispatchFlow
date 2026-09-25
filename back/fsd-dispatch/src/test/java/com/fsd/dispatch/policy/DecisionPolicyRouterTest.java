package com.fsd.dispatch.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.config.DispatchPolicyProperties;
import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.DecisionOutcome;
import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.core.DecisionWeights;
import com.fsd.dispatch.core.ForecastAwarePolicy;
import com.fsd.dispatch.core.RulePolicy;
import com.fsd.dispatch.metrics.DispatchDecisionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 三阶段晋级路由器（§2.2）的行为断言。
 *
 * <p>钉住的是三条"做错了不会报错、只会静默改错"的性质：SHADOW 不得改变结果、分桶必须稳定、
 * 挑战者抛错必须回落在位策略。
 */
class DecisionPolicyRouterTest {

    private static final DecisionWeights WEIGHTS = new DecisionWeights(
            1D, 0D, 0D, 0D, 0D, 100,
            DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR, DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
            DecisionWeights.DEFAULT_PEAK_SOC_DAMPING, DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);

    private SimpleMeterRegistry meterRegistry;
    private DispatchPolicyProperties properties;
    private DecisionPolicyRouter router;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new DispatchPolicyProperties();
        router = new DecisionPolicyRouter(new RulePolicy(),
                List.of(new RulePolicy(), new ForecastAwarePolicy(), new ThrowingPolicy()),
                properties,
                new DispatchDecisionMetrics(meterRegistry));
    }

    @Test
    void offAndShadowAlwaysUseIncumbent() {
        properties.setMode(DispatchPolicyProperties.Mode.OFF);
        assertEquals("RULE", router.selectForOrder("order:1").id());
        assertFalse(router.shadowActive());

        properties.setMode(DispatchPolicyProperties.Mode.SHADOW);
        assertEquals("RULE", router.selectForOrder("order:1").id());
        assertTrue(router.shadowActive());
    }

    @Test
    void grayBucketingIsStableAndRespondsToPercent() {
        properties.setMode(DispatchPolicyProperties.Mode.GRAY);
        properties.setGrayPercent(0);
        assertEquals("RULE", router.selectForOrder("order:42").id());
        properties.setGrayPercent(100);
        assertEquals("FORECAST", router.selectForOrder("order:42").id());

        properties.setGrayPercent(50);
        int challengerSide = 0;
        for (int i = 0; i < 200; i++) {
            String key = "order:" + i;
            String first = router.selectForOrder(key).id();
            assertEquals(first, router.selectForOrder(key).id(), "同一单重放必须落同一侧: " + key);
            if (!"RULE".equals(first)) {
                challengerSide++;
            }
        }
        assertTrue(challengerSide > 0 && challengerSide < 200,
                "50% 灰度既不能全落一侧也不能全落另一侧，实际 " + challengerSide + "/200");
    }

    @Test
    void shadowWithZeroPressureAgreesAndRecordsRegretZero() {
        properties.setMode(DispatchPolicyProperties.Mode.SHADOW);
        DecisionInput input = input(candidate(1L, "ZJF-AV-01", 100D, 0D),
                candidate(2L, "ZJF-AV-02", 120D, 0D));
        DecisionOutcome incumbent = router.selectForOrder("order:7").decide(input);
        DecisionTrace trace = new DecisionTrace();
        router.shadowCompare(input, incumbent, trace);

        assertEquals(1L, incumbent.ranked().get(0).vehicleId());
        assertEquals("FORECAST", trace.getShadowPolicyId());
        assertEquals("ZJF-AV-01", trace.getShadowWinnerCode());
        assertTrue(trace.getShadowAgreed());
        assertEquals(0D, trace.getShadowRegret());
        assertEquals(1D, meterRegistry.get("dispatchflow.dispatch.shadow.compare")
                .tags("policy", "FORECAST", "agree", "true").counter().count());
    }

    /**
     * 分歧用例：B 距离更近但目标站点即将饱和。在位策略选 B，影子策略选 A ——
     * 一致率掉下来、regret 给出"按在位标尺这一换亏多少"，两条数字正是 §2.2 晋级判据要的。
     */
    @Test
    void shadowDisagreementIsRecordedWithoutChangingIncumbentOutcome() {
        properties.setMode(DispatchPolicyProperties.Mode.SHADOW);
        DecisionInput input = input(candidate(1L, "ZJF-AV-01", 100D, 0D),
                candidate(2L, "ZJF-AV-02", 60D, 1D));
        DecisionOutcome incumbent = new RulePolicy().decide(input);
        assertEquals(2L, incumbent.ranked().get(0).vehicleId(), "在位策略仍选距离更近的 B");

        DecisionTrace trace = new DecisionTrace();
        router.shadowCompare(input, incumbent, trace);

        assertFalse(trace.getShadowAgreed());
        assertEquals("ZJF-AV-01", trace.getShadowWinnerCode());
        // regret = 在位标尺下 A 的分 - B 的分 = 100 - 60
        assertEquals(40D, trace.getShadowRegret(), 1e-6);
        assertEquals(1D, meterRegistry.get("dispatchflow.dispatch.shadow.compare")
                .tags("policy", "FORECAST", "agree", "false").counter().count());
        assertEquals(40D, meterRegistry.get("dispatchflow.dispatch.shadow.regret")
                .summary().mean(), 1e-6);
    }

    @Test
    void unregisteredChallengerStaysOnIncumbentAndRegisteredThrowerFallsBack() {
        properties.setMode(DispatchPolicyProperties.Mode.PRIMARY);
        properties.setChallenger("NOPE");
        DecisionInput input = input(candidate(1L, "ZJF-AV-01", 100D, 0D));
        assertEquals("RULE", router.selectForOrder("order:9").id());
        assertEquals(1D, meterRegistry.get("dispatchflow.dispatch.policy.fallback")
                .tag("policy", "NOPE").counter().count());

        properties.setChallenger("THROWING");
        DecisionOutcome outcome = router.selectForOrder("order:9").decide(input);
        assertEquals("RULE", outcome.policyId(), "挑战者抛错必须回落在位策略，不能把派单打挂");
        assertEquals(1D, meterRegistry.get("dispatchflow.dispatch.policy.fallback")
                .tag("policy", "THROWING").counter().count());
    }

    @Test
    void runtimeOverrideBeatsConfigAndResetHandsBack() {
        properties.setMode(DispatchPolicyProperties.Mode.OFF);
        router.applyOverride(DispatchPolicyProperties.Mode.PRIMARY, 100, "FORECAST");
        assertEquals("FORECAST", router.selectForOrder("order:3").id());
        assertTrue(router.overrideActive());
        router.clearOverride();
        assertEquals("RULE", router.selectForOrder("order:3").id());
        assertFalse(router.overrideActive());
    }

    @Test
    void shadowFailureIsSwallowedWithoutHalfWrittenTrace() {
        properties.setMode(DispatchPolicyProperties.Mode.SHADOW);
        properties.setChallenger("THROWING");
        DecisionInput input = input(candidate(1L, "ZJF-AV-01", 100D, 0D));
        DecisionTrace trace = new DecisionTrace();
        router.shadowCompare(input, new RulePolicy().decide(input), trace);
        assertNull(trace.getShadowPolicyId(), "影子抛错不得留下半成品记录");
        assertEquals(1D, meterRegistry.get("dispatchflow.dispatch.shadow.compare")
                .tags("policy", "THROWING", "agree", "error").counter().count());
    }

    private static DecisionInput.CandidateState candidate(Long id, String code, double metres, double pressure) {
        return new DecisionInput.CandidateState(new DecisionInput.RankedCandidateIdentity(id, code),
                90, metres, false, 10L, pressure);
    }

    private static DecisionInput input(DecisionInput.CandidateState... candidates) {
        return new DecisionInput("NORMAL", false, 1D, WEIGHTS, List.of(candidates));
    }

    /** 只用于验证回退与影子抛错路径的挑战者替身。 */
    private static final class ThrowingPolicy implements DecisionPolicy {

        @Override
        public String id() {
            return "THROWING";
        }

        @Override
        public String version() {
            return "throwing-v1";
        }

        @Override
        public DecisionOutcome decide(DecisionInput input) {
            throw new IllegalStateException("boom");
        }
    }
}
