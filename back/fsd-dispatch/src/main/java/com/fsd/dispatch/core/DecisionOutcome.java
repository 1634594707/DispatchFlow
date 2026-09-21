package com.fsd.dispatch.core;

import java.util.List;

/**
 * 策略输出：按"总分越小越优"排好序的候选清单。
 *
 * @param ranked   升序候选；空列表表示无可用候选
 * @param policyId 产出该结果的策略标识，进决策快照
 * @param policyVersion 产出该结果的策略版本
 */
public record DecisionOutcome(List<RankedCandidate> ranked, String policyId, String policyVersion) {

    public DecisionOutcome {
        ranked = ranked == null ? List.of() : List.copyOf(ranked);
    }

    public boolean isEmpty() {
        return ranked.isEmpty();
    }
}
