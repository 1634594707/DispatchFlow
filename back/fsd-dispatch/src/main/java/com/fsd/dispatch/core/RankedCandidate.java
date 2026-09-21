package com.fsd.dispatch.core;

/**
 * 一个候选车的分项打分（§7.1 决策内核，纯数据）。
 *
 * <p>分项必须完整保留而不是只留总分：决策快照（§7.3）要能回答"为什么选这台车、比次优差多少分"，
 * 影子对照要比的是分项而不是一个标量。刻意不持有 ORM 实体，内核因此可整体离线回放或换语言移植。
 */
public record RankedCandidate(DecisionInput.RankedCandidateIdentity identity,
                              double distanceScore,
                              double socScore,
                              double pluggedBonus,
                              double idleBonus,
                              double priorityFactor,
                              double totalScore) {

    public RankedCandidate(Long vehicleId, String vehicleCode,
                           double distanceScore, double socScore, double pluggedBonus,
                           double idleBonus, double priorityFactor, double totalScore) {
        this(new DecisionInput.RankedCandidateIdentity(vehicleId, vehicleCode),
                distanceScore, socScore, pluggedBonus, idleBonus, priorityFactor, totalScore);
    }

    public Long vehicleId() {
        return identity == null ? null : identity.vehicleId();
    }

    public String vehicleCode() {
        return identity == null ? null : identity.vehicleCode();
    }
}
