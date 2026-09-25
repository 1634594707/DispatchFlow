package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_decision_snapshot")
public class DispatchDecisionSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private Long taskId;

    private Long orderId;

    private String orderNo;

    private String policyId;

    private String policyVersion;

    private String shadowPolicyId;

    private String shadowPolicyVersion;

    private String shadowWinnerCode;

    /** 1=影子与在位策略 top-1 一致，0=不一致，NULL=本单未跑影子。 */
    private Integer shadowAgreed;

    private BigDecimal shadowRegret;

    private Long profileId;

    private String profileType;

    private Integer grayBucket;

    private Integer experimentSide;

    private Integer candidateTotal;

    private Integer freshTelemetryCount;

    private Integer socEligibleCount;

    private Integer socChainEligibleCount;

    private Integer reachableCount;

    private Integer candidateEvaluated;

    private String candidatesJson;

    private Long winnerVehicleId;

    private String winnerVehicleCode;

    private BigDecimal winnerScore;

    private BigDecimal runnerUpScore;

    private BigDecimal scoreGap;

    private Integer tieCount;

    private String matchAlgorithm;

    private String roadGraphVersion;

    private String failReason;

    private Long durationMicros;

    private BigDecimal confidence;

    private LocalDateTime generatedAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
