package com.fsd.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.vo.AdminDecisionExplainResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.dispatch.service.DispatchDecisionSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 决策解释读接口（路线图 §7.3「任务详情页展示 top-3 候选对比」的后端那一半）。
 *
 * <p>此前快照表只有写侧：{@code DispatchDecisionSnapshotService} 只有一个 {@code record(...)}，
 * 全仓没有任何查询方法与管理端端点 ⇒ "任一历史任务可回答为什么选这台车"只能人肉进库查 JSON。
 */
@RestController
@RequestMapping("/api/admin/dispatch/decisions")
@Tag(name = "Dispatch Decisions", description = "Read-side explanation of past vehicle-selection decisions")
@SecurityRequirement(name = "adminToken")
public class AdminDecisionSnapshotController {

    private static final int DEFAULT_LIMIT = 5;

    private final DispatchDecisionSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    public AdminDecisionSnapshotController(DispatchDecisionSnapshotService snapshotService,
                                          ObjectMapper objectMapper) {
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(summary = "List decision snapshots for a task or order",
            description = "Returns the candidate funnel and per-candidate score breakdown, newest first")
    public ApiResponse<List<AdminDecisionExplainResponse>> list(@RequestParam(required = false) Long taskId,
                                                                @RequestParam(required = false) Long orderId,
                                                                @RequestParam(required = false) Integer limit,
                                                                HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        if (taskId == null && orderId == null) {
            return ApiResponse.success(List.of());
        }
        int capped = limit == null ? DEFAULT_LIMIT : limit;
        List<DispatchDecisionSnapshotEntity> rows = taskId != null
                ? snapshotService.latestByTask(taskId, capped)
                : snapshotService.latestByOrder(orderId, capped);
        List<AdminDecisionExplainResponse> explained = new ArrayList<>(rows.size());
        for (DispatchDecisionSnapshotEntity row : rows) {
            explained.add(toResponse(row));
        }
        return ApiResponse.success(explained);
    }

    private AdminDecisionExplainResponse toResponse(DispatchDecisionSnapshotEntity row) {
        List<AdminDecisionExplainResponse.Candidate> candidates = readCandidates(row.getCandidatesJson());
        return AdminDecisionExplainResponse.builder()
                .snapshotId(row.getId())
                .taskId(row.getTaskId())
                .orderId(row.getOrderId())
                .orderNo(row.getOrderNo())
                .policyId(row.getPolicyId())
                .policyVersion(row.getPolicyVersion())
                .matchAlgorithm(row.getMatchAlgorithm())
                .roadGraphVersion(row.getRoadGraphVersion())
                .generatedAt(row.getGeneratedAt())
                .durationMicros(row.getDurationMicros())
                .failReason(row.getFailReason())
                .remark(row.getRemark())
                .funnel(AdminDecisionExplainResponse.Funnel.builder()
                        .candidateTotal(row.getCandidateTotal())
                        .freshTelemetry(row.getFreshTelemetryCount())
                        .socEligible(row.getSocEligibleCount())
                        .socChainEligible(row.getSocChainEligibleCount())
                        .reachable(row.getReachableCount())
                        .evaluated(row.getCandidateEvaluated())
                        .build())
                .winner(pickWinner(candidates, row.getWinnerVehicleId()))
                .runnerUpScore(row.getRunnerUpScore())
                .scoreGap(row.getScoreGap())
                .tieCount(row.getTieCount())
                .candidates(candidates)
                .shadow(row.getShadowPolicyId() == null ? null
                        : AdminDecisionExplainResponse.Shadow.builder()
                        .policyId(row.getShadowPolicyId())
                        .policyVersion(row.getShadowPolicyVersion())
                        .winnerCode(row.getShadowWinnerCode())
                        .agreed(row.getShadowAgreed() == null ? null : row.getShadowAgreed() == 1)
                        .regret(row.getShadowRegret())
                        .build())
                .build();
    }

    private static AdminDecisionExplainResponse.Candidate pickWinner(
            List<AdminDecisionExplainResponse.Candidate> candidates, Long winnerVehicleId) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (winnerVehicleId == null) {
            return candidates.get(0);
        }
        return candidates.stream()
                .filter(candidate -> winnerVehicleId.equals(candidate.getVehicleId()))
                .findFirst()
                .orElse(candidates.get(0));
    }

    /** 快照里存的是写侧序列化的候选数组；解析失败只丢候选，不丢整条解释。 */
    private List<AdminDecisionExplainResponse.Candidate> readCandidates(String candidatesJson) {
        List<AdminDecisionExplainResponse.Candidate> candidates = new ArrayList<>();
        if (candidatesJson == null || candidatesJson.isBlank()) {
            return candidates;
        }
        try {
            JsonNode root = objectMapper.readTree(candidatesJson);
            if (!root.isArray()) {
                return candidates;
            }
            int rank = 1;
            for (JsonNode node : root) {
                candidates.add(AdminDecisionExplainResponse.Candidate.builder()
                        .rank(rank++)
                        .vehicleId(node.path("vehicleId").isNumber() ? node.path("vehicleId").asLong() : null)
                        .vehicleCode(node.path("vehicleCode").asText(null))
                        .distance(node.path("distance").asDouble(0D))
                        .socMargin(node.path("socMargin").asDouble(0D))
                        .pluggedBonus(node.path("pluggedBonus").asDouble(0D))
                        .idleBonus(node.path("idleBonus").asDouble(0D))
                        .priorityFactor(node.path("priorityFactor").asDouble(1D))
                        .forecastPenalty(node.path("forecastPenalty").asDouble(0D))
                        .total(node.path("total").asDouble(0D))
                        .build());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return List.of();
        }
        return candidates;
    }
}
