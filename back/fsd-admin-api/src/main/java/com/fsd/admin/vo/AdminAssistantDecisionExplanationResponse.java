package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * P2-1 只读调度助手：单笔订单的决策解释。
 *
 * <p>数据全部来自 {@code t_dispatch_decision_snapshot}（决策快照），解释文本由快照字段
 * 确定性生成——"为什么选这台车"的答案在快照里本来就有，助手只负责把它讲成人话。
 */
@Data
@Builder
public class AdminAssistantDecisionExplanationResponse {

    private Long orderId;

    private String orderNo;

    private String policyId;

    private String policyVersion;

    private String failReason;

    private String failReasonText;

    /** 漏斗：候选总数 → 新鲜遥测 → SOC 达标 → 全链路可达 → 实际评估 */
    private Integer candidateTotal;

    private Integer freshTelemetryCount;

    private Integer socEligibleCount;

    private Integer socChainEligibleCount;

    private Integer reachableCount;

    private Integer candidateEvaluated;

    private String winnerVehicleCode;

    private BigDecimal winnerScore;

    private BigDecimal runnerUpScore;

    private BigDecimal scoreGap;

    private Integer tieCount;

    private List<CandidateComponent> topCandidates;

    /** 影子对照（本单未跑影子时为 null） */
    private Shadow shadow;

    /** 确定性生成的解释文本（三分钟讲法的单例版） */
    private String explanation;

    private LocalDateTime generatedAt;

    @Data
    @Builder
    public static class CandidateComponent {

        private String vehicleCode;

        private BigDecimal total;

        private BigDecimal distance;

        private BigDecimal socMargin;

        private BigDecimal idleBonus;

        private BigDecimal pluggedBonus;

        private BigDecimal forecastPenalty;
    }

    @Data
    @Builder
    public static class Shadow {

        private String shadowPolicyId;

        private Boolean agreed;

        private BigDecimal regret;
    }
}
