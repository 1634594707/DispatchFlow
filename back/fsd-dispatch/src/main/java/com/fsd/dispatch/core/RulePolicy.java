package com.fsd.dispatch.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 规则策略（§2.1 实现 A）：现有打分算式的等价迁移，逐位保持修复前行为。
 *
 * <p>语义要点（都是有意为之，不是遗留疏忽）：
 * <ul>
 *   <li><b>总分越小越优</b>，所以停车位奖励与空闲奖励是从总分里减掉的</li>
 *   <li>优先级用乘法系数而不是加分：HIGH 缩小总分（更容易被选中），LOW 放大总分</li>
 *   <li>高峰时段只削弱 SOC 惩罚（距离更重要），不削弱距离项</li>
 *   <li>插电奖励要求"插电 + STANDBY + 满电"三者同时成立，且随距离线性衰减</li>
 * </ul>
 */
public final class RulePolicy implements DecisionPolicy {

    public static final String POLICY_ID = "RULE";
    public static final String POLICY_VERSION = "rule-v1";

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
        DecisionWeights weights = input.weights();
        double priorityFactor = resolvePriorityFactor(input.orderPriority(), weights);
        List<RankedCandidate> ranked = new ArrayList<>(input.candidates().size());
        for (DecisionInput.CandidateState candidate : input.candidates()) {
            ranked.add(score(candidate, input.peakMode(), input.peakDistanceFactor(), weights, priorityFactor));
        }
        ranked.sort(rankOrder());
        return new DecisionOutcome(ranked, POLICY_ID, POLICY_VERSION);
    }

    /**
     * 排序：总分升序，<b>并列时按 `vehicleCode` 字典序</b>（§7.2「并列裁决没有显式规则」）。
     *
     * <p>这是相对修复前的<b>有意偏离</b>：以前只比总分，{@code List.sort} 的稳定排序让并列由
     * "候选数组的来路顺序"（即 DB 返回顺序）决定 —— M 档实测出现过 {@code score_gap=0.0000 / tie_count=2}
     * （同泊位、同 SOC 的两台车），那种"可复现"其实只是碰巧。现在并列有明确、可解释、与查询顺序无关的次级键。
     *
     * <p>次级键选 {@code vehicleCode} 而不是"派单次数最少优先"：后者要在热路径多查一张表，
     * 而 §13.21 实测候选集就是全量在线空闲车（每台候选还要跑两次路径规划），不值得为它加一次查询。
     * {@code vehicleId} 兜底用于代码为空的脏数据（不让 NPE 变成派单失败）。
     */
    static Comparator<RankedCandidate> rankOrder() {
        return Comparator.comparingDouble(RankedCandidate::totalScore)
                .thenComparing(RankedCandidate::vehicleCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RankedCandidate::vehicleId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private RankedCandidate score(DecisionInput.CandidateState candidate, boolean peakMode,
                                  double peakDistanceFactor, DecisionWeights weights, double priorityFactor) {
        double distanceScore = candidate.roadDistanceMetres() * weights.weightDistance();
        // 非高峰时外层解析出来的就是 1.0，这里不再分支
        distanceScore *= peakDistanceFactor;
        double socScore = (weights.fullSoc() - candidate.soc()) * weights.weightSocMargin();
        if (peakMode) {
            socScore *= weights.peakSocDamping();
        }
        double pluggedBonus = 0D;
        if (candidate.pluggedFullStandby() && candidate.soc() == weights.fullSoc()) {
            pluggedBonus = weights.weightPluggedStandbyBonus()
                    * Math.max(0D, 1D - candidate.roadDistanceMetres() / weights.pluggedBonusFalloffMetres());
        }
        double idleBonus = candidate.idleMinutes() > 0
                ? Math.min(candidate.idleMinutes() * weights.weightFairness(), weights.maxIdleBonus())
                : 0D;
        double total = (distanceScore + socScore - pluggedBonus - idleBonus) * priorityFactor;
        return new RankedCandidate(candidate.identity(), distanceScore, socScore,
                pluggedBonus, idleBonus, priorityFactor, total);
    }

    static double resolvePriorityFactor(String priority, DecisionWeights weights) {
        if ("HIGH".equalsIgnoreCase(priority)) {
            return weights.priorityHighFactor();
        } else if ("LOW".equalsIgnoreCase(priority)) {
            return weights.priorityLowFactor();
        }
        return 1D;
    }

    /** 决策解释原文，与修复前 {@code DispatchVehicleAssignServiceImpl} 的格式逐字一致。 */
    public static String explain(RankedCandidate best, boolean geoBlendOn, boolean mapfOn) {
        return String.format(Locale.ROOT,
                "Selected %s: distance=%.1f, socPenalty=%.1f, pluggedBonus=%.1f, idleBonus=%.1f, priorityFactor=%.2f, total=%.1f%s%s",
                best.vehicleCode(),
                best.distanceScore(),
                best.socScore(),
                best.pluggedBonus(),
                best.idleBonus(),
                best.priorityFactor(),
                best.totalScore(),
                geoBlendOn ? ", geoBlend=on" : "",
                mapfOn ? ", mapf=on" : "");
    }
}
