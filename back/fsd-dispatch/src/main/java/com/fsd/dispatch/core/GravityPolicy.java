package com.fsd.dispatch.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求引力 / 运力压力策略（面试路线图 P0-3，最小可解释版）。
 *
 * <p>在 {@link RulePolicy} 之上加两个可解释项（委托而非复制，性质与 {@link ForecastAwarePolicy} 同构）：
 * <pre>
 * stationPressure(d, w, s) = d × w / max(s, 1)          —— 运力压力：需求越大、供给越少越紧
 * attraction(d, r)         = d / (r_km + ε_km)²         —— 需求引力：目的地越热、距离越近越有引力
 * total = ruleTotal + W_pull × stationPressure(homeDemand, 1, homeSupply)
 *                    − W_attr × attraction(destinationDemand, roadDistanceMetres / 1000)
 * </pre>
 *
 * <p>语义（三分钟讲法的骨架）：
 * <ul>
 *   <li><b>拉离惩罚（运力压力）</b>：把一台车从"在窗需求高、空闲供给少"的站区拉走，是这笔派单的绝对成本
 *       —— 与这单多急无关，所以不乘优先级系数（同 {@link ForecastAwarePolicy} 的取舍）。</li>
 *   <li><b>目的地引力</b>：车送完这单会落在卸货站区；那里在窗需求越大，车落位越有价值，按引力公式给抵扣。
 *       距离按 <b>km</b> 进平方项——用米会把该项平方压死（1100 m ⇒ 1/1.2e6），这是单位口径不是近似误差，
 *       ε 默认 0.05 km（50 m，远小于最近站距，不改变单调性）。</li>
 *   <li><b>硬约束优先</b>：本策略只重排调用方给定的候选清单；SOC 全链路可达与路网可达在候选构建时已被
 *       过滤（生产 canCompleteTaskWithSoc 语义），引力分数永远碰不到不可达车——这是构造保证，不是约定。</li>
 *   <li><b>失败回退</b>：候选不带引力事实（{@link DecisionInput.GravityView#NONE}）时引力项恒 0，
 *       逐位等于 {@link RulePolicy}；预测缺失在调用方就退化为 NONE。</li>
 * </ul>
 *
 * <p>批量撮合：HUNGARIAN 的代价矩阵用的就是各候选 totalScore，本策略接管时引力项自动进入成本矩阵——
 * 同一台车配高需求目的地 vs 低需求目的地的差别，逐单贪心看不见，全局撮合看得见。
 *
 * <p>{@code RankedCandidate.forecastPenalty} 槽位在本策略承载净引力调整（pull − attr，可负）：
 * 快照里"这一策略的调整项改了多少分"沿用同一个回答口子。
 */
public final class GravityPolicy implements DecisionPolicy {

    public static final String POLICY_ID = "GRAVITY";
    public static final String POLICY_VERSION = "gravity-v1";

    /** 拉离惩罚权重（米等效）。供给 1 台、在窗需求 3 单的站区 ≈ 300 m 等效惩罚——值得绕但压不过硬约束。 */
    public static final double DEFAULT_PULL_WEIGHT = 100.0D;

    /** 目的地引力权重（米等效）。attraction 量纲是 单/km²，× 权重后折成米等效抵扣。 */
    public static final double DEFAULT_ATTRACTION_WEIGHT = 50.0D;

    /** 引力公式的距离平滑项（km）：只防 0 距离爆炸，取值远小于最近站距量级，不改变单调性。 */
    public static final double DEFAULT_EPSILON_KM = 0.05D;

    private final RulePolicy rulePolicy;
    private final double pullWeight;
    private final double attractionWeight;
    private final double epsilonKm;

    public GravityPolicy() {
        this(DEFAULT_PULL_WEIGHT, DEFAULT_ATTRACTION_WEIGHT, DEFAULT_EPSILON_KM);
    }

    public GravityPolicy(double pullWeight, double attractionWeight) {
        this(pullWeight, attractionWeight, DEFAULT_EPSILON_KM);
    }

    GravityPolicy(double pullWeight, double attractionWeight, double epsilonKm) {
        this.rulePolicy = new RulePolicy();
        this.pullWeight = pullWeight;
        this.attractionWeight = attractionWeight;
        this.epsilonKm = epsilonKm;
    }

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
        DecisionOutcome base = rulePolicy.decide(input);
        if (base.isEmpty() || !anyGravity(input)) {
            // 构造保证的失败回退：没有任何引力事实 ⇒ 逐位等于 RULE（只换策略标识）
            return new DecisionOutcome(base.ranked(), POLICY_ID, POLICY_VERSION);
        }
        Map<DecisionInput.RankedCandidateIdentity, Double> distanceById = new HashMap<>();
        Map<DecisionInput.RankedCandidateIdentity, DecisionInput.GravityView> gravityById = new HashMap<>();
        for (DecisionInput.CandidateState candidate : input.candidates()) {
            distanceById.put(candidate.identity(), candidate.roadDistanceMetres());
            gravityById.put(candidate.identity(),
                    candidate.gravity() == null ? DecisionInput.GravityView.NONE : candidate.gravity());
        }

        List<RankedCandidate> adjusted = new ArrayList<>(base.ranked().size());
        for (RankedCandidate ranked : base.ranked()) {
            DecisionInput.GravityView g =
                    gravityById.getOrDefault(ranked.identity(), DecisionInput.GravityView.NONE);
            double pull = pullWeight == 0D ? 0D
                    : stationPressure(g.homeDemand(), 1.0D, g.homeSupply()) * pullWeight;
            double rKm = Math.max(0D, distanceById.getOrDefault(ranked.identity(), 0D)) / 1000D;
            double attr = attractionWeight == 0D ? 0D
                    : attraction(g.destinationDemand(), rKm, epsilonKm) * attractionWeight;
            double net = pull - attr;
            adjusted.add(new RankedCandidate(ranked.identity(),
                    ranked.distanceScore(), ranked.socScore(), ranked.pluggedBonus(),
                    ranked.idleBonus(), ranked.priorityFactor(), net,
                    ranked.totalScore() + net));
        }
        adjusted.sort(RulePolicy.rankOrder());
        return new DecisionOutcome(adjusted, POLICY_ID, POLICY_VERSION);
    }

    /**
     * 运力压力（P0-3 公式）：{@code d × w / max(s, 1)}。供给 0 视同 1（max 的语义）——
     * 需求单调增、供给单调减（减到 1 为底），两条性质是构造的，测试钉住。
     */
    public static double stationPressure(double predictedDemand, double priorityWeight, double availableSupply) {
        return predictedDemand * priorityWeight / Math.max(availableSupply, 1.0D);
    }

    /** 需求引力（P0-3 公式）：{@code d / (r_km + ε)²}，r 与 ε 同为 km 口径，距离单调减。 */
    public static double attraction(double demandWeight, double distanceKm, double epsilonKm) {
        double r = Math.max(0D, distanceKm) + Math.max(0D, epsilonKm);
        return demandWeight / (r * r);
    }

    private static boolean anyGravity(DecisionInput input) {
        for (DecisionInput.CandidateState candidate : input.candidates()) {
            if (candidate.gravity() != null && !candidate.gravity().isNone()) {
                return true;
            }
        }
        return false;
    }
}
