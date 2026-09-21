package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.dispatch.mapper.DispatchDecisionSnapshotMapper;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.entity.VehicleEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 决策快照的取值契约（§7.3）：能否回答「为什么选这台车、差多少分」。 */
class DispatchDecisionSnapshotServiceImplTest {

    private final DispatchDecisionSnapshotMapper mapper = mock(DispatchDecisionSnapshotMapper.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DispatchDecisionSnapshotServiceImpl service =
            new DispatchDecisionSnapshotServiceImpl(mapper, registry);

    /** 快照写入结果：`result=ok|failed`。降级为日志这件事本身要能被看见。 */
    private double snapshotCount(String result) {
        Counter counter = registry.find(DispatchDecisionSnapshotServiceImpl.SNAPSHOT_WRITE_METRIC)
                .tag("result", result).counter();
        return counter == null ? 0D : counter.count();
    }

    private static VehicleEntity vehicle(long id, String code) {
        VehicleEntity entity = new VehicleEntity();
        entity.setId(id);
        entity.setVehicleCode(code);
        return entity;
    }

    private static RankedCandidate candidate(long id, String code, double total) {
        return new RankedCandidate(id, code, total, 0D, 0D, 0D, 1.0D, total);
    }

    private static DecisionTrace trace(List<RankedCandidate> ranked) {
        DecisionTrace trace = new DecisionTrace();
        trace.setParkId(1L);
        trace.setCandidateTotal(4);
        trace.setReachable(ranked.size());
        trace.setRanked(ranked);
        trace.setProfileId(7L);
        trace.setProfileType("EXPERIMENT");
        trace.setGrayPercent(30);
        trace.setGrayBucket(12);
        trace.setProductionSide(false);
        return trace;
    }

    private static OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setId(1001L);
        order.setOrderNo("SO-1001");
        order.setDispatchTaskId(55L);
        return order;
    }

    private DispatchDecisionSnapshotEntity capture() {
        ArgumentCaptor<DispatchDecisionSnapshotEntity> captor =
                ArgumentCaptor.forClass(DispatchDecisionSnapshotEntity.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsWinnerRunnerUpAndGapForTheSelectedVehicle() {
        List<RankedCandidate> ranked = List.of(
                candidate(1L, "ZJF-AV-01", 10.0D),
                candidate(2L, "ZJF-AV-02", 14.5D),
                candidate(3L, "ZJF-AV-03", 22.0D));
        DispatchAssignResult result = DispatchAssignResult.success(
                vehicle(1L, "ZJF-AV-01"), "Selected ZJF-AV-01", 10.0D, 8.0D, 2.0D, 0D);

        service.record(order(), 1L, trace(ranked), result, 4321L);

        DispatchDecisionSnapshotEntity saved = capture();
        assertEquals(55L, saved.getTaskId());
        assertEquals("ZJF-AV-01", saved.getWinnerVehicleCode());
        assertEquals(10.0D, saved.getWinnerScore().doubleValue());
        assertEquals(4.5D, saved.getScoreGap().doubleValue());
        assertEquals(1, saved.getTieCount());
        assertEquals(4, saved.getCandidateTotal());
        assertEquals(3, saved.getCandidateEvaluated());
        assertEquals(4321L, saved.getDurationMicros());
        assertEquals(7L, saved.getProfileId());
        assertEquals(1, saved.getExperimentSide());
        assertNull(saved.getFailReason());
        assertTrue(saved.getCandidatesJson().contains("ZJF-AV-03"));
    }

    @Test
    void gapGoesNegativeWhenMapfPicksALowerScoringVehicle() {
        List<RankedCandidate> ranked = List.of(
                candidate(1L, "ZJF-AV-01", 10.0D),
                candidate(2L, "ZJF-AV-02", 14.5D));
        DispatchAssignResult result = DispatchAssignResult.success(
                vehicle(2L, "ZJF-AV-02"), "Selected ZJF-AV-02", 14.5D, 12.0D, 2.5D, 0D);

        service.record(order(), 1L, trace(ranked), result, 100L);

        DispatchDecisionSnapshotEntity saved = capture();
        // 为避时空冲突放弃更优候选时，分差为负 —— 这是可证明性要保留的信息，不是脏数据
        assertEquals(-4.5D, saved.getScoreGap().doubleValue());
        assertEquals("ZJF-AV-02", saved.getWinnerVehicleCode());
    }

    @Test
    void countsTiesAtTheTopScore() {
        List<RankedCandidate> ranked = List.of(
                candidate(1L, "ZJF-AV-01", 10.0D),
                candidate(2L, "ZJF-AV-02", 10.0D),
                candidate(3L, "ZJF-AV-03", 12.0D));
        DispatchAssignResult result = DispatchAssignResult.success(
                vehicle(1L, "ZJF-AV-01"), "Selected ZJF-AV-01", 10.0D, 8.0D, 2.0D, 0D);

        service.record(order(), 1L, trace(ranked), result, 100L);

        DispatchDecisionSnapshotEntity saved = capture();
        assertEquals(2, saved.getTieCount());
        assertEquals(0.0D, saved.getScoreGap().doubleValue());
    }

    @Test
    void failureDecisionsAreRecordedWithTheirFailReasonAndFunnel() {
        DecisionTrace trace = trace(List.of());
        trace.setFreshTelemetry(2);
        trace.setSocEligible(1);
        DispatchAssignResult result = DispatchAssignResult.failure(
                DispatchAssignFailReason.UNREACHABLE, "Pickup station is not reachable");

        service.record(order(), 1L, trace, result, 50L);

        DispatchDecisionSnapshotEntity saved = capture();
        assertEquals("UNREACHABLE", saved.getFailReason());
        assertEquals(4, saved.getCandidateTotal());
        assertEquals(0, saved.getCandidateEvaluated());
        assertNull(saved.getWinnerVehicleCode());
        assertNotNull(saved.getGeneratedAt());
    }

    @Test
    void snapshotWriteFailureDoesNotBreakDispatch() {
        doThrow(new RuntimeException("db down")).when(mapper).insert(any(DispatchDecisionSnapshotEntity.class));
        List<RankedCandidate> ranked = List.of(candidate(1L, "ZJF-AV-01", 10.0D));
        DispatchAssignResult result = DispatchAssignResult.success(
                vehicle(1L, "ZJF-AV-01"), "Selected ZJF-AV-01", 10.0D, 8.0D, 2.0D, 0D);

        service.record(order(), 1L, trace(ranked), result, 100L);

        assertEquals(1D, snapshotCount("failed"), 1e-9,
                "写失败必须留下指标点：只有 WARN 日志的降级，事后无法被告警发现（同 §13.15 那条教训）");
        assertEquals(0D, snapshotCount("ok"), 1e-9);
    }

    @Test
    void successfulSnapshotWriteIsAlsoCounted() {
        List<RankedCandidate> ranked = List.of(candidate(1L, "ZJF-AV-01", 10.0D));
        DispatchAssignResult result = DispatchAssignResult.success(
                vehicle(1L, "ZJF-AV-01"), "Selected ZJF-AV-01", 10.0D, 8.0D, 2.0D, 0D);

        service.record(order(), 1L, trace(ranked), result, 100L);

        assertEquals(1D, snapshotCount("ok"), 1e-9, "有分母才能算失败率");
        assertEquals(0D, snapshotCount("failed"), 1e-9);
    }
}
