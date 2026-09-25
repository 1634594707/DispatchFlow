package com.fsd.dispatch.metrics;

import com.fsd.dispatch.dispatch.DispatchAssignResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 派单算法侧指标（路线图 §7.3「算法侧指标补齐」）。
 *
 * <p>此前这些量只能靠日志与决策快照事后数，Prometheus 侧一个都没有：派单成功率、选车时延、
 * 失败原因分布、策略降级次数、影子一致率与 regret、批量撮合收益。本类是它们**唯一**的仪表面，
 * 所有计数与耗时都存在 Micrometer 里（不再另存 AtomicLong，否则又会出现"面板有数、抓取无值"）。
 *
 * <p>标签基数受控：outcome 二值、failReason 取自 {@link DispatchAssignFailReason} 枚举、
 * policy/stage 是有限集合，不引入车辆号/订单号这类高基数标签。
 */
@Component
public class DispatchDecisionMetrics {

    /** 派单结果计数：{@code outcome=success|failure} + {@code failReason}。成功率与失败分布同一条指标。 */
    static final String ASSIGN_RESULT = "dispatchflow.dispatch.assign.result";
    /** 选车决策耗时（含漏斗过滤与打分，不含任务锁等待）。 */
    static final String ASSIGN_DURATION = "dispatchflow.dispatch.assign.duration";
    /** 三阶段晋级路由：每次派单实际由哪个策略产出结果。 */
    static final String POLICY_ROUTE = "dispatchflow.dispatch.policy.route";
    /** 策略降级：挑战者策略抛错被回退到在位策略的次数（§2.2 回退必须可见）。 */
    static final String POLICY_FALLBACK = "dispatchflow.dispatch.policy.fallback";
    /** 影子对照 top-1 一致率。 */
    static final String SHADOW_COMPARE = "dispatchflow.dispatch.shadow.compare";
    /** 影子 regret：按在位策略的标尺，影子选择比实际选择差多少分。 */
    static final String SHADOW_REGRET = "dispatchflow.dispatch.shadow.regret";
    /** 批量撮合每 tick 结果计数。 */
    static final String BATCH_RESULT = "dispatchflow.dispatch.batch.result";
    /** 批量撮合单 tick 耗时（撮合自身耗时，§2.3 收益指标之一）。 */
    static final String BATCH_DURATION = "dispatchflow.dispatch.batch.duration";
    /** 批量撮合相对贪心的分数节省量（越大越省）。 */
    static final String BATCH_SAVINGS = "dispatchflow.dispatch.batch.savings";

    private final MeterRegistry registry;
    private final Timer assignDuration;
    private final Timer batchDuration;
    private final DistributionSummary shadowRegret;
    private final DistributionSummary batchSavings;

    public DispatchDecisionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.assignDuration = Timer.builder(ASSIGN_DURATION)
                .description("Vehicle-selection decision latency per order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.batchDuration = Timer.builder(BATCH_DURATION)
                .description("Cost-matrix build plus solve time per batch matching tick")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.shadowRegret = DistributionSummary.builder(SHADOW_REGRET)
                .description("Score gap of the shadow policy's choice measured on the incumbent policy's scale")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.batchSavings = DistributionSummary.builder(BATCH_SAVINGS)
                .description("Total score saved by batch matching against the greedy baseline in the same tick")
                .register(registry);
    }

    /** 一次派单决策的结果与耗时。成功与失败都记，失败按原因分标签（此前 LOW_SOC 会吞掉别的成因，见 §13.23）。 */
    public void recordAssignment(DispatchAssignResult result, Duration elapsed) {
        boolean success = result != null && result.isSuccess();
        String failReason;
        if (success) {
            failReason = "NONE";
        } else if (result == null) {
            // 漏斗之前就抛了业务异常（园区暂停等）：既不是成功，也不能并进某个失败原因桶，
            // 否则"无车可派"的占比会把异常量一起算进去 —— 又是一次 LOW_SOC 式压平
            failReason = "EXCEPTION";
        } else {
            failReason = result.getFailReason() == null ? "UNKNOWN" : result.getFailReason().name();
        }
        registry.counter(ASSIGN_RESULT,
                "outcome", success ? "success" : "failure",
                "failReason", failReason).increment();
        long micros = Math.max(0L, elapsed.toNanos() / 1_000L);
        assignDuration.record(micros, TimeUnit.MICROSECONDS);
    }

    public void recordPolicyRoute(String stage, String policyId) {
        registry.counter(POLICY_ROUTE, "stage", stage, "policy", policyId).increment();
    }

    public void recordPolicyFallback(String policyId) {
        registry.counter(POLICY_FALLBACK, "policy", policyId).increment();
    }

    /** 影子对照：top-1 是否一致，以及（可得时）regret。 */
    public void recordShadowCompare(String policyId, boolean agreed, Double regret) {
        registry.counter(SHADOW_COMPARE, "policy", policyId, "agree", String.valueOf(agreed)).increment();
        if (regret != null && regret >= 0D) {
            shadowRegret.record(regret);
        }
    }

    public void recordShadowFailure(String policyId) {
        registry.counter(SHADOW_COMPARE, "policy", policyId, "agree", "error").increment();
    }

    public void recordBatchResult(String outcome, int count) {
        if (count <= 0) {
            return;
        }
        registry.counter(BATCH_RESULT, "outcome", outcome).increment(count);
    }

    public void recordBatchDuration(Duration elapsed) {
        batchDuration.record(Math.max(0L, elapsed.toNanos()), TimeUnit.NANOSECONDS);
    }

    public void recordBatchSavings(double savings) {
        if (savings > 0D) {
            batchSavings.record(savings);
        }
    }

    public long assignmentCount() {
        return registry.get(ASSIGN_RESULT).counters().stream().mapToInt(c -> (int) c.count()).sum();
    }
}
