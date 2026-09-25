package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 一次派单决策的可证明解释（路线图 §7.3）。
 *
 * <p>回答的是三件事：<b>为什么是这台车</b>（候选分项）、<b>比次优好多少</b>（分差与并列数）、
 * <b>漏斗在哪一层把候选清掉</b>（失败时）。数字全部来自 {@code t_dispatch_decision_snapshot}，
 * 不做二次计算，避免解释与实际判定漂移。
 */
@Data
@Builder
public class AdminDecisionExplainResponse {

    private Long snapshotId;

    private Long taskId;

    private Long orderId;

    private String orderNo;

    private String policyId;

    private String policyVersion;

    /** {@code GREEDY} / {@code HUNGARIAN}：这一单是逐单贪心得到的还是批量撮合得到的（§2.3）。 */
    private String matchAlgorithm;

    private String roadGraphVersion;

    private LocalDateTime generatedAt;

    private Long durationMicros;

    private String failReason;

    private String remark;

    private Funnel funnel;

    private Candidate winner;

    private BigDecimal runnerUpScore;

    private BigDecimal scoreGap;

    private Integer tieCount;

    private List<Candidate> candidates;

    private Shadow shadow;

    /** 候选漏斗逐层存活数：NULL 表示没走到那一层，与"走到但该层为 0"是两件事。 */
    @Data
    @Builder
    public static class Funnel {
        private Integer candidateTotal;
        private Integer freshTelemetry;
        private Integer socEligible;
        private Integer socChainEligible;
        private Integer reachable;
        private Integer evaluated;
    }

    /** 单个候选的分项打分（总分越小越优）。 */
    @Data
    @Builder
    public static class Candidate {
        private Integer rank;
        private Long vehicleId;
        private String vehicleCode;
        private Double distance;
        private Double socMargin;
        private Double pluggedBonus;
        private Double idleBonus;
        private Double priorityFactor;
        private Double forecastPenalty;
        private Double total;
    }

    /** 影子对照（§2.2 SHADOW）：未跑影子时整个对象为 null，而不是全 0。 */
    @Data
    @Builder
    public static class Shadow {
        private String policyId;
        private String policyVersion;
        private String winnerCode;
        private Boolean agreed;
        private BigDecimal regret;
    }
}
