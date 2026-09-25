package com.fsd.dispatch.core;

/**
 * 一个候选车的分项打分（§7.1 决策内核，纯数据）。
 *
 * <p>分项必须完整保留而不是只留总分：决策快照（§7.3）要能回答"为什么选这台车、比次优差多少分"，
 * 影子对照要比的是分项而不是一个标量。刻意不持有 ORM 实体，内核因此可整体离线回放或换语言移植。
 *
 * @param forecastPenalty 预测感知的站点压力惩罚（§2.1 实现 B）；规则策略与未接线时为 0，
 *                        总分里已含该项，单独留存是为了能回答"错峰这一项改了多少分"
 */
public record RankedCandidate(DecisionInput.RankedCandidateIdentity identity,
                              double distanceScore,
                              double socScore,
                              double pluggedBonus,
                              double idleBonus,
                              double priorityFactor,
                              double forecastPenalty,
                              double totalScore) {

    /** 无预测项的构造（规则策略、§7.3 快照写入、既有测试都用这条），forecastPenalty 恒为 0。 */
    public RankedCandidate(DecisionInput.RankedCandidateIdentity identity,
                           double distanceScore, double socScore, double pluggedBonus,
                           double idleBonus, double priorityFactor, double totalScore) {
        this(identity, distanceScore, socScore, pluggedBonus, idleBonus, priorityFactor, 0D, totalScore);
    }

    public RankedCandidate(Long vehicleId, String vehicleCode,
                           double distanceScore, double socScore, double pluggedBonus,
                           double idleBonus, double priorityFactor, double totalScore) {
        this(new DecisionInput.RankedCandidateIdentity(vehicleId, vehicleCode),
                distanceScore, socScore, pluggedBonus, idleBonus, priorityFactor, 0D, totalScore);
    }

    public Long vehicleId() {
        return identity == null ? null : identity.vehicleId();
    }

    public String vehicleCode() {
        return identity == null ? null : identity.vehicleCode();
    }
}
