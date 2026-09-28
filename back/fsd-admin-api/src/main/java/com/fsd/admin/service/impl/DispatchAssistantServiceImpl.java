package com.fsd.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.service.DispatchAssistantService;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAssistantBriefingResponse;
import com.fsd.admin.vo.AdminAssistantDecisionExplanationResponse;
import com.fsd.admin.vo.AdminAssistantDigestResponse;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.dispatch.mapper.DispatchDecisionSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 只读调度助手实现（P2-1）。
 *
 * <p>数据源与口径全部复用既有服务：效率/异常/每日摘要来自 {@link AnalyticsAdminService}，
 * 决策解释来自 {@code t_dispatch_decision_snapshot}——助手是这些只读数据的"讲人话"层。
 * 确定性摘要不需要大模型也在场；将来的 Spring AI 对话层（需 Boot 3.4 升级，见路线图 P2-1）
 * 只消费本服务的结果，不新增任何写路径。
 */
@Service
public class DispatchAssistantServiceImpl implements DispatchAssistantService {

    /** 快照近貌采样条数：漏斗均值与影子一致率的窗口。 */
    static final int SNAPSHOT_SAMPLE = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnalyticsAdminService analyticsAdminService;
    private final DispatchDecisionSnapshotMapper snapshotMapper;

    public DispatchAssistantServiceImpl(AnalyticsAdminService analyticsAdminService,
                                        DispatchDecisionSnapshotMapper snapshotMapper) {
        this.analyticsAdminService = analyticsAdminService;
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public AdminAssistantBriefingResponse getBriefing(String period, Long parkId) {
        AdminAnalyticsEfficiencyResponse efficiency =
                analyticsAdminService.getEfficiency(period, parkId);
        var exceptions = analyticsAdminService.getExceptionAnalysis(period, parkId);
        var forecast = analyticsAdminService.getEnergyForecast(LocalDate.now(), parkId);
        return AdminAssistantBriefingResponse.builder()
                .period(period)
                .parkId(parkId)
                .dispatchMetrics(efficiency.getDispatchMetrics())
                .forecast(AdminAssistantBriefingResponse.Forecast.builder()
                        .enabled(forecast.isEnabled())
                        .anyData(forecast.isAnyData())
                        .stationCount(forecast.getStationCount())
                        .forecastDate(forecast.getForecastDate())
                        .build())
                .snapshotStats(buildSnapshotStats())
                .exceptionTypes(exceptions.getTypeDistribution())
                .build();
    }

    @Override
    public AdminAssistantDecisionExplanationResponse explainDecision(Long orderId) {
        List<DispatchDecisionSnapshotEntity> snapshots =
                snapshotMapper.selectList(new LambdaQueryWrapper<DispatchDecisionSnapshotEntity>()
                        .eq(DispatchDecisionSnapshotEntity::getOrderId, orderId)
                        .eq(DispatchDecisionSnapshotEntity::getDeleted, 0)
                        .orderByDesc(DispatchDecisionSnapshotEntity::getGeneratedAt)
                        .orderByDesc(DispatchDecisionSnapshotEntity::getId));
        if (snapshots.isEmpty()) {
            throw new com.fsd.common.exception.BusinessException(
                    "ASSISTANT_SNAPSHOT_NOT_FOUND", "该订单没有决策快照");
        }
        DispatchDecisionSnapshotEntity snapshot = snapshots.get(0);
        return explain(snapshot);
    }

    @Override
    public AdminAssistantDigestResponse getDigest(LocalDate date, Long parkId) {
        var daily = analyticsAdminService.getDailySummary(date, parkId);
        var exceptions = analyticsAdminService.getExceptionAnalysis("day", parkId);
        return AdminAssistantDigestResponse.builder()
                .date(date == null ? LocalDate.now().toString() : date.toString())
                .parkId(parkId)
                .orderTotal(daily.getOrderTotal())
                .orderCompleted(daily.getOrderCompleted())
                .orderCompletionRate(daily.getOrderCompletionRate())
                .taskTotal(daily.getTaskTotal())
                .taskSuccess(daily.getTaskSuccess())
                .openExceptionCount(daily.getOpenExceptionCount())
                .avgResolutionMinutes(exceptions.getAvgResolutionMinutes())
                .exceptionTypes(exceptions.getTypeDistribution())
                .rootCauseHints(exceptions.getRootCauseHints())
                .build();
    }

    private AdminAssistantBriefingResponse.SnapshotStats buildSnapshotStats() {
        List<DispatchDecisionSnapshotEntity> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<DispatchDecisionSnapshotEntity>()
                        .eq(DispatchDecisionSnapshotEntity::getDeleted, 0)
                        .orderByDesc(DispatchDecisionSnapshotEntity::getId)
                        .last("LIMIT " + SNAPSHOT_SAMPLE));
        if (snapshots.isEmpty()) {
            return AdminAssistantBriefingResponse.SnapshotStats.builder()
                    .sampled(0).meanCandidateTotal(0D).shadowAgreedRate(Double.NaN).shadowRows(0).build();
        }
        long candidates = 0L;
        int shadowRows = 0;
        int shadowAgreed = 0;
        for (DispatchDecisionSnapshotEntity snapshot : snapshots) {
            candidates += snapshot.getCandidateTotal() == null ? 0 : snapshot.getCandidateTotal();
            if (snapshot.getShadowAgreed() != null) {
                shadowRows++;
                if (snapshot.getShadowAgreed() == 1) {
                    shadowAgreed++;
                }
            }
        }
        return AdminAssistantBriefingResponse.SnapshotStats.builder()
                .sampled(snapshots.size())
                .meanCandidateTotal(candidates / (double) snapshots.size())
                .shadowAgreedRate(shadowRows == 0 ? Double.NaN : shadowAgreed / (double) shadowRows)
                .shadowRows(shadowRows)
                .build();
    }

    AdminAssistantDecisionExplanationResponse explain(DispatchDecisionSnapshotEntity snapshot) {
        List<AdminAssistantDecisionExplanationResponse.CandidateComponent> candidates =
                parseCandidates(snapshot.getCandidatesJson());
        String failReason = snapshot.getFailReason();
        AdminAssistantDecisionExplanationResponse response = AdminAssistantDecisionExplanationResponse
                .builder()
                .orderId(snapshot.getOrderId())
                .orderNo(snapshot.getOrderNo())
                .policyId(snapshot.getPolicyId())
                .policyVersion(snapshot.getPolicyVersion())
                .failReason(failReason)
                .failReasonText(failReasonText(failReason))
                .candidateTotal(snapshot.getCandidateTotal())
                .freshTelemetryCount(snapshot.getFreshTelemetryCount())
                .socEligibleCount(snapshot.getSocEligibleCount())
                .socChainEligibleCount(snapshot.getSocChainEligibleCount())
                .reachableCount(snapshot.getReachableCount())
                .candidateEvaluated(snapshot.getCandidateEvaluated())
                .winnerVehicleCode(snapshot.getWinnerVehicleCode())
                .winnerScore(snapshot.getWinnerScore())
                .runnerUpScore(snapshot.getRunnerUpScore())
                .scoreGap(snapshot.getScoreGap())
                .tieCount(snapshot.getTieCount())
                .topCandidates(candidates)
                .shadow(snapshot.getShadowPolicyId() == null ? null
                        : AdminAssistantDecisionExplanationResponse.Shadow.builder()
                        .shadowPolicyId(snapshot.getShadowPolicyId())
                        .agreed(snapshot.getShadowAgreed() == null ? null
                                : snapshot.getShadowAgreed() == 1)
                        .regret(snapshot.getShadowRegret())
                        .build())
                .explanation(buildExplanation(snapshot, candidates))
                .generatedAt(LocalDateTime.now())
                .build();
        return response;
    }

    private String failReasonText(String failReason) {
        if (failReason == null) {
            return null;
        }
        return switch (failReason) {
            case "NO_VEHICLE" -> "没有空闲车可派（车队全忙或全离线）";
            case "LOW_SOC" -> "候选车 SOC 低于可派线（含全链路回桩校验不过）";
            case "MATCH_LOST" -> "撮合丢失：候选在配对时已被别的单占用";
            case "DISPATCH_PAUSED" -> "派单全局暂停开关在位";
            case "PARK_SCOPE_DENIED" -> "订单越出园区受理范围";
            default -> "原因码原样保留：" + failReason;
        };
    }

    private String buildExplanation(DispatchDecisionSnapshotEntity snapshot,
                                    List<AdminAssistantDecisionExplanationResponse.CandidateComponent> candidates) {
        StringBuilder sb = new StringBuilder();
        if (snapshot.getFailReason() != null) {
            sb.append("本单未完成派车：").append(failReasonText(snapshot.getFailReason())).append("。");
        } else if (snapshot.getWinnerVehicleCode() != null) {
            sb.append("本单派给 ").append(snapshot.getWinnerVehicleCode())
                    .append("（总分 ").append(snapshot.getWinnerScore())
                    .append("，打分越小越优）。");
        }
        if (snapshot.getCandidateTotal() != null && snapshot.getCandidateTotal() > 0
                && snapshot.getReachableCount() != null) {
            sb.append("漏斗：候选 ").append(snapshot.getCandidateTotal())
                    .append(" → 新鲜遥测 ").append(snapshot.getFreshTelemetryCount())
                    .append(" → SOC 达标 ").append(snapshot.getSocEligibleCount())
                    .append(" → 全链路回桩可达 ").append(snapshot.getSocChainEligibleCount())
                    .append(" → 路网可达 ").append(snapshot.getReachableCount())
                    .append(" → 评分 ").append(snapshot.getCandidateEvaluated())
                    .append("。");
        }
        if (snapshot.getScoreGap() != null) {
            if (snapshot.getScoreGap().doubleValue() < 0D) {
                sb.append("分差为负：MAPF 为避让时空冲突放弃了分数更优的候选，这是有意保留的信息。");
            } else {
                sb.append("次优分差 ").append(snapshot.getScoreGap());
                if (snapshot.getTieCount() != null && snapshot.getTieCount() > 1) {
                    sb.append("，并列 ").append(snapshot.getTieCount()).append(" 个（按 vehicleCode 裁决）");
                }
                sb.append("。");
            }
        }
        if (!candidates.isEmpty()) {
            AdminAssistantDecisionExplanationResponse.CandidateComponent top = candidates.get(0);
            sb.append("Top-1 分量：距离 ").append(top.getDistance())
                    .append(" / SOC 惩罚 ").append(top.getSocMargin())
                    .append(" / 插枪奖励 -").append(top.getPluggedBonus())
                    .append(" / 空闲奖励 -").append(top.getIdleBonus())
                    .append(" / 策略调整 ").append(top.getForecastPenalty())
                    .append("。");
        }
        if (snapshot.getShadowPolicyId() != null && snapshot.getShadowAgreed() != null) {
            sb.append("影子对照（").append(snapshot.getShadowPolicyId()).append("）：")
                    .append(snapshot.getShadowAgreed() == 1 ? "top-1 一致" : "选择不同")
                    .append(snapshot.getShadowRegret() == null ? "" :
                            "，regret=" + snapshot.getShadowRegret() + "（按在位标尺）")
                    .append("。");
        }
        return sb.toString();
    }

    private List<AdminAssistantDecisionExplanationResponse.CandidateComponent> parseCandidates(String json) {
        List<AdminAssistantDecisionExplanationResponse.CandidateComponent> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            JsonNode array = MAPPER.readTree(json);
            for (JsonNode node : array) {
                rows.add(AdminAssistantDecisionExplanationResponse.CandidateComponent.builder()
                        .vehicleCode(node.path("vehicleCode").asText(null))
                        .total(node.path("total").decimalValue())
                        .distance(node.path("distance").decimalValue())
                        .socMargin(node.path("socMargin").decimalValue())
                        .idleBonus(node.path("idleBonus").decimalValue())
                        .pluggedBonus(node.path("pluggedBonus").decimalValue())
                        .forecastPenalty(node.path("forecastPenalty").decimalValue())
                        .build());
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return rows;
    }
}
