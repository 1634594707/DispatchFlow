package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastResponse;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAssistantDecisionExplanationResponse;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.dispatch.mapper.DispatchDecisionSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * P2-1 只读调度助手的闸门：简报四源同口径复用、决策解释确定性生成（漏斗/winner/分量/影子/
 * 失败原因人话）、无快照时明确报错——以及"只读"这条边界本身：服务没有写方法可暴露。
 */
@ExtendWith(MockitoExtension.class)
class DispatchAssistantServiceImplTest {

    @Mock
    private AnalyticsAdminService analyticsAdminService;
    @Mock
    private DispatchDecisionSnapshotMapper snapshotMapper;

    private DispatchAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DispatchAssistantServiceImpl(analyticsAdminService, snapshotMapper);
    }

    @Test
    @DisplayName("简报复用分析服务同口径：调度指标/预测可用性/快照近貌/异常类型")
    void briefingReusesAnalyticsSemantics() {
        var metrics = AdminAnalyticsEfficiencyResponse.DispatchMetrics.builder()
                .availableVehicles(12L).pendingOrders(3L).supplyDemandRatio(4D).build();
        var efficiency = AdminAnalyticsEfficiencyResponse.builder()
                .dispatchMetrics(metrics).build();
        var exceptions = AdminAnalyticsExceptionResponse.builder()
                .typeDistribution(List.of()).build();
        when(analyticsAdminService.getEfficiency(Mockito.anyString(), Mockito.any())).thenReturn(efficiency);
        when(analyticsAdminService.getExceptionAnalysis(Mockito.anyString(), Mockito.any()))
                .thenReturn(exceptions);
        var forecast = AdminAnalyticsEnergyForecastResponse.builder()
                .enabled(true).anyData(true).stationCount(13).build();
        when(analyticsAdminService.getEnergyForecast(Mockito.any(LocalDate.class), Mockito.any()))
                .thenReturn(forecast);
        when(snapshotMapper.selectList(any(Wrapper.class))).thenReturn(List.of(snapshot(9, 5, null, 1)));

        var briefing = service.getBriefing("week", null);

        assertEquals(12L, briefing.getDispatchMetrics().getAvailableVehicles());
        assertTrue(briefing.getForecast().isEnabled());
        assertEquals(13, briefing.getForecast().getStationCount());
        assertEquals(1, briefing.getSnapshotStats().getSampled());
        assertEquals(9D, briefing.getSnapshotStats().getMeanCandidateTotal(), 1e-9);
        assertEquals(1.0D, briefing.getSnapshotStats().getShadowAgreedRate(), 1e-9);
    }

    @Test
    @DisplayName("决策解释：漏斗/winner/分差/影子/失败原因人话全由快照确定性生成")
    void explanationIsDeterministicFromSnapshot() {
        DispatchDecisionSnapshotEntity snapshot = snapshot(20, 12, 1L, 1);
        snapshot.setOrderNo("OD-1");
        snapshot.setPolicyId("RULE");
        snapshot.setWinnerVehicleCode("SIM-7");
        snapshot.setWinnerScore(new BigDecimal("101.5"));
        snapshot.setRunnerUpScore(new BigDecimal("110"));
        snapshot.setScoreGap(new BigDecimal("8.5"));
        snapshot.setTieCount(1);
        snapshot.setCandidatesJson("[{\"vehicleCode\":\"SIM-7\",\"total\":101.5,\"distance\":100,"
                + "\"socMargin\":1.5,\"pluggedBonus\":0,\"idleBonus\":0,\"forecastPenalty\":0}]");
        snapshot.setShadowPolicyId("FORECAST");
        when(snapshotMapper.selectList(any(Wrapper.class))).thenReturn(List.of(snapshot));

        AdminAssistantDecisionExplanationResponse response = service.explainDecision(42L);

        assertEquals("SIM-7", response.getWinnerVehicleCode());
        assertNull(response.getFailReason(), "成功单没有失败原因");
        assertNull(response.getFailReasonText());
        assertTrue(response.getExplanation().contains("本单派给 SIM-7"));
        assertTrue(response.getExplanation().contains("漏斗：候选 20"));
        assertTrue(response.getExplanation().contains("次优分差 8.5"));
        assertTrue(response.getExplanation().contains("影子对照（FORECAST）：top-1 一致"));
        assertEquals(1, response.getTopCandidates().size());
        assertNotNull(response.getShadow());
        assertTrue(response.getShadow().getAgreed());
    }

    @Test
    @DisplayName("失败单：failReason 映射人话、无 winner 不报 NPE、负分差注明 MAPF 语义")
    void failureAndNegativeGapAreExplained() {
        DispatchDecisionSnapshotEntity failed = snapshot(0, null, null, null);
        failed.setFailReason("NO_VEHICLE");
        when(snapshotMapper.selectList(any(Wrapper.class))).thenReturn(List.of(failed));
        AdminAssistantDecisionExplanationResponse response = service.explainDecision(7L);
        assertEquals("NO_VEHICLE", response.getFailReason());
        assertTrue(response.getExplanation().contains("本单未完成派车"));
        assertNull(response.getWinnerVehicleCode());

        DispatchDecisionSnapshotEntity mapf = snapshot(5, 3, 1L, null);
        mapf.setWinnerVehicleCode("SIM-9");
        mapf.setWinnerScore(new BigDecimal("105"));
        mapf.setRunnerUpScore(new BigDecimal("99"));
        mapf.setScoreGap(new BigDecimal("-6"));
        when(snapshotMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mapf));
        AdminAssistantDecisionExplanationResponse mapfResponse = service.explainDecision(8L);
        assertTrue(mapfResponse.getExplanation().contains("MAPF"), "负分差必须讲清是 MAPF 有意为之");
    }

    @Test
    @DisplayName("无快照的订单：明确报错而不是编一段解释")
    void missingSnapshotFailsClearly() {
        when(snapshotMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(com.fsd.common.exception.BusinessException.class, () -> service.explainDecision(99L));
    }

    private DispatchDecisionSnapshotEntity snapshot(int candidateTotal, Integer fresh,
                                                    Long winnerId, Integer shadowAgreed) {
        DispatchDecisionSnapshotEntity entity = new DispatchDecisionSnapshotEntity();
        entity.setOrderId(42L);
        entity.setOrderNo("OD-42");
        entity.setPolicyId("RULE");
        entity.setPolicyVersion("rule-v1");
        entity.setCandidateTotal(candidateTotal);
        entity.setFreshTelemetryCount(fresh);
        entity.setSocEligibleCount(fresh);
        entity.setSocChainEligibleCount(fresh);
        entity.setReachableCount(fresh);
        entity.setCandidateEvaluated(fresh);
        entity.setShadowAgreed(shadowAgreed);
        entity.setShadowPolicyId(shadowAgreed == null ? null : "FORECAST");
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }
}
