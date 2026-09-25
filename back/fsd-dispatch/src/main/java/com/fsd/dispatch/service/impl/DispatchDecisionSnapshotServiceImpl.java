package com.fsd.dispatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.dispatch.mapper.DispatchDecisionSnapshotMapper;
import com.fsd.dispatch.service.DispatchDecisionSnapshotService;
import com.fsd.order.entity.OrderEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DispatchDecisionSnapshotServiceImpl implements DispatchDecisionSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(DispatchDecisionSnapshotServiceImpl.class);

    /** 快照写入结果计数器：{@code result=ok|failed}。只有日志等于没有告警面。 */
    static final String SNAPSHOT_WRITE_METRIC = "dispatchflow.decision_snapshot.write";

    /** 快照里保留的候选数量：任务详情页展示 top-3，多留两条供对照实验回放。 */
    private static final int MAX_CANDIDATES_KEPT = 5;
    private static final double TIE_EPSILON = 1e-9D;

    private final DispatchDecisionSnapshotMapper snapshotMapper;
    private final MeterRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DispatchDecisionSnapshotServiceImpl(DispatchDecisionSnapshotMapper snapshotMapper,
                                               MeterRegistry registry) {
        this.snapshotMapper = snapshotMapper;
        this.registry = registry;
    }

    @Override
    public void record(OrderEntity order, Long parkId, DecisionTrace trace,
                       DispatchAssignResult result, long durationMicros) {
        try {
            snapshotMapper.insert(toEntity(order, parkId, trace, result, durationMicros));
            counter("ok").increment();
        } catch (RuntimeException ex) {
            // 决策已作出，快照写失败只能降级为日志：可证明性不能反过来成为派单的可用性依赖。
            // 但"降级"本身必须有指标面：这条日志是事后追问"为什么没有快照"时唯一的线索来源
            counter("failed").increment();
            log.warn("dispatch decision snapshot write failed: order={} park={} err={}",
                    order == null ? null : order.getOrderNo(), parkId, ex.toString());
        }
    }

    private Counter counter(String outcome) {
        return registry.counter(SNAPSHOT_WRITE_METRIC, "result", outcome);
    }

    @Override
    public java.util.List<DispatchDecisionSnapshotEntity> latestByTask(Long taskId, int limit) {
        if (taskId == null) {
            return java.util.List.of();
        }
        return snapshotMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<DispatchDecisionSnapshotEntity>()
                .eq(DispatchDecisionSnapshotEntity::getTaskId, taskId)
                .eq(DispatchDecisionSnapshotEntity::getDeleted, 0)
                .orderByDesc(DispatchDecisionSnapshotEntity::getGeneratedAt)
                .orderByDesc(DispatchDecisionSnapshotEntity::getId)
                .last("LIMIT " + clamped(limit)));
    }

    @Override
    public java.util.List<DispatchDecisionSnapshotEntity> latestByOrder(Long orderId, int limit) {
        if (orderId == null) {
            return java.util.List.of();
        }
        return snapshotMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<DispatchDecisionSnapshotEntity>()
                .eq(DispatchDecisionSnapshotEntity::getOrderId, orderId)
                .eq(DispatchDecisionSnapshotEntity::getDeleted, 0)
                .orderByDesc(DispatchDecisionSnapshotEntity::getGeneratedAt)
                .orderByDesc(DispatchDecisionSnapshotEntity::getId)
                .last("LIMIT " + clamped(limit)));
    }

    /** limit 走整数钳制而不是拼接字符串：这是唯一进 SQL 的调用方输入，不能带任何形状。 */
    private static int clamped(int limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    DispatchDecisionSnapshotEntity toEntity(OrderEntity order, Long parkId, DecisionTrace trace,
                                            DispatchAssignResult result, long durationMicros) {
        DispatchDecisionSnapshotEntity entity = new DispatchDecisionSnapshotEntity();
        entity.setParkId(parkId);
        entity.setOrderId(order == null ? null : order.getId());
        entity.setOrderNo(order == null ? null : order.getOrderNo());
        entity.setTaskId(order == null ? null : order.getDispatchTaskId());
        entity.setPolicyId(trace.getPolicyId());
        entity.setPolicyVersion(trace.getPolicyVersion());
        entity.setShadowPolicyId(trace.getShadowPolicyId());
        entity.setShadowPolicyVersion(trace.getShadowPolicyVersion());
        entity.setShadowWinnerCode(trace.getShadowWinnerCode());
        if (trace.getShadowAgreed() != null) {
            entity.setShadowAgreed(trace.getShadowAgreed() ? 1 : 0);
        }
        if (trace.getShadowRegret() != null) {
            entity.setShadowRegret(money(trace.getShadowRegret()));
        }
        entity.setMatchAlgorithm(trace.getMatchAlgorithm());
        entity.setRoadGraphVersion(trace.getRoadGraphVersion());
        entity.setCandidateTotal(trace.getCandidateTotal());
        if (trace.getCandidateTotal() > 0) {
            // 没进漏斗就留 NULL，而不是写一排 0 —— 否则「压根没车可派」和
            // 「暂停/容量满被提前挡下」在失败原因分布里会长得一模一样
            entity.setFreshTelemetryCount(trace.getFreshTelemetry());
            entity.setSocEligibleCount(trace.getSocEligible());
            entity.setSocChainEligibleCount(trace.getSocChainEligible());
            entity.setReachableCount(trace.getReachable());
        }
        entity.setDurationMicros(durationMicros);
        entity.setGeneratedAt(LocalDateTime.now());

        entity.setProfileId(trace.getProfileId());
        entity.setProfileType(trace.getProfileType());
        entity.setGrayBucket(trace.getGrayBucket());
        entity.setExperimentSide(trace.isProductionSide() ? 0 : 1);

        List<RankedCandidate> ranked = trace.getRanked();
        entity.setCandidateEvaluated(ranked.size());
        entity.setCandidatesJson(writeCandidates(ranked));

        if (result != null && !result.isSuccess()) {
            entity.setFailReason(result.getFailReason() == null ? null : result.getFailReason().name());
            entity.setRemark(truncate(result.getMessage()));
            return entity;
        }

        RankedCandidate winner = findWinner(ranked, result);
        if (winner != null) {
            entity.setWinnerVehicleId(winner.vehicleId());
            entity.setWinnerVehicleCode(winner.vehicleCode());
            entity.setWinnerScore(money(winner.totalScore()));
            double runnerUp = bestAlternativeScore(ranked, winner);
            entity.setRunnerUpScore(money(runnerUp));
            entity.setScoreGap(money(runnerUp - winner.totalScore()));
            entity.setTieCount(countTies(ranked, winner.totalScore()));
        }
        // MAPF 为避时空冲突可能放弃分数更优的候选，此时 winner 不在 ranked 首位、分差为负，
        // 这是有意保留的信息而不是噪声
        entity.setRemark(truncate(result == null ? null : result.getMessage()));
        return entity;
    }

    private RankedCandidate findWinner(List<RankedCandidate> ranked, DispatchAssignResult result) {
        if (result == null || result.getVehicle() == null) {
            return ranked.isEmpty() ? null : ranked.get(0);
        }
        Long chosenId = result.getVehicle().getId();
        return ranked.stream()
                .filter(candidate -> candidate.vehicleId() != null && candidate.vehicleId().equals(chosenId))
                .findFirst()
                .orElse(ranked.isEmpty() ? null : ranked.get(0));
    }

    /**
     * 次优分：除选中车外分数最低（最好）的候选。{@code ranked} 已按总分升序，
     * 因此第一个不是选中车的候选就是次优；只有一个候选时返回自身，分差为 0。
     */
    private double bestAlternativeScore(List<RankedCandidate> ranked, RankedCandidate winner) {
        for (RankedCandidate candidate : ranked) {
            if (candidate != winner) {
                return candidate.totalScore();
            }
        }
        return winner.totalScore();
    }

    private int countTies(List<RankedCandidate> ranked, double winnerScore) {
        int ties = 0;
        for (RankedCandidate candidate : ranked) {
            if (Math.abs(candidate.totalScore() - winnerScore) <= TIE_EPSILON) {
                ties++;
            }
        }
        return ties;
    }

    private String writeCandidates(List<RankedCandidate> ranked) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RankedCandidate candidate : ranked.stream().limit(MAX_CANDIDATES_KEPT).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vehicleId", candidate.vehicleId());
            row.put("vehicleCode", candidate.vehicleCode());
            row.put("distance", candidate.distanceScore());
            row.put("socMargin", candidate.socScore());
            row.put("pluggedBonus", candidate.pluggedBonus());
            row.put("idleBonus", candidate.idleBonus());
            row.put("priorityFactor", candidate.priorityFactor());
            row.put("forecastPenalty", candidate.forecastPenalty());
            row.put("total", candidate.totalScore());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "[]";
        }
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
