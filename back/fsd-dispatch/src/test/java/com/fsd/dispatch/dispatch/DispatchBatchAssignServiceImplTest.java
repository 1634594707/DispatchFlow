package com.fsd.dispatch.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.config.DispatchPolicyProperties;
import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.metrics.DispatchDecisionMetrics;
import com.fsd.order.entity.OrderEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 批量撮合（§2.3）的矩阵与降级行为。
 *
 * <p>这里测的是"配对算法有没有比贪心拿到更好的总分配"，不是撮合收益的真实幅度 ——
 * 后者要在 M 档场景集上带置信区间地量，已由仿真实验台交付（《已完成工作记录》§13.13）。
 */
class DispatchBatchAssignServiceImplTest {

    private DispatchVehicleAssignService assignService;
    private DispatchPolicyProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private DispatchBatchAssignServiceImpl batchService;

    @BeforeEach
    void setUp() {
        assignService = Mockito.mock(DispatchVehicleAssignService.class);
        properties = new DispatchPolicyProperties();
        meterRegistry = new SimpleMeterRegistry();
        batchService = new DispatchBatchAssignServiceImpl(assignService, properties,
                new DispatchDecisionMetrics(meterRegistry));
    }

    /**
     * 贪心的失稳点：两单都想抢同一台近车，先到者拿走之后，另一单只能吃远车。
     * 匈牙利给出的全局最优是把近车让给"差别更大"的那单，总分更低。
     */
    @Test
    void hungarianBeatsGreedyOnSharedVehicleContention() {
        // 订单 1：V1=100 V2=110；订单 2：V1=105 V2=400
        OrderEntity first = order(1L);
        OrderEntity second = order(2L);
        when(assignService.rankCandidatesForBatch(first)).thenAnswer(invocation -> List.of(
                candidate(10L, "ZJF-AV-01", 100D), candidate(11L, "ZJF-AV-02", 110D)));
        when(assignService.rankCandidatesForBatch(second)).thenAnswer(invocation -> List.of(
                candidate(10L, "ZJF-AV-01", 105D), candidate(11L, "ZJF-AV-02", 400D)));

        DispatchBatchAssignService.BatchAssignOutcome outcome =
                batchService.assignOrders(List.of(first, second));

        assertEquals("HUNGARIAN", outcome.algorithm());
        assertEquals(2, outcome.matched());
        assertEquals(2, outcome.greedyMatched());
        // 贪心：1→V1(100) 2→V2(400) = 500；匈牙利：1→V2(110) 2→V1(105) = 215
        assertEquals(500D, outcome.greedyTotalScore(), 1e-6);
        assertEquals(215D, outcome.totalScore(), 1e-6);
        assertEquals(285D, outcome.savings(), 1e-6);
        // 提交顺序按分数升序：最划算的 (2→V1) 排在前面
        assertEquals(List.of(2L, 1L), List.copyOf(outcome.planInOrder().keySet()));
        assertEquals("ZJF-AV-01", outcome.planInOrder().get(2L));
    }

    @Test
    void greedyAlgorithmSettingSkipsSolverButUsesSameMatrix() {
        properties.setBatchAlgorithm("GREEDY");
        when(assignService.rankCandidatesForBatch(any())).thenAnswer(invocation -> List.of(
                candidate(10L, "ZJF-AV-01", 100D), candidate(11L, "ZJF-AV-02", 110D)));
        DispatchBatchAssignService.BatchAssignOutcome outcome =
                batchService.assignOrders(List.of(order(1L), order(2L)));
        assertEquals("GREEDY", outcome.algorithm());
        assertEquals(2, outcome.matched());
        // 两侧同一份矩阵、同一算法 ⇒ 节省量不可比之外还恰好为 0，配对数相同所以仍报数
        assertEquals(0D, outcome.savings(), 1e-6);
    }

    /** 单多于车：矩阵转置后每车至多配一单，剩下的单必须留空而不是被硬塞。 */
    @Test
    void unmatchedOrdersStayEmptyWhenVehiclesAreScarcer() {
        when(assignService.rankCandidatesForBatch(any())).thenAnswer(invocation -> List.of(
                candidate(10L, "ZJF-AV-01", 100D)));
        DispatchBatchAssignService.BatchAssignOutcome outcome =
                batchService.assignOrders(List.of(order(1L), order(2L), order(3L)));
        assertEquals(3, outcome.orders());
        assertEquals(1, outcome.matched());
        assertEquals(1, outcome.greedyMatched());
        assertEquals("ZJF-AV-01", outcome.planInOrder().values().iterator().next());
    }

    @Test
    void emptyPoolIsANoOp() {
        DispatchBatchAssignService.BatchAssignOutcome outcome = batchService.assignOrders(List.of());
        assertEquals(0, outcome.orders());
        assertTrue(outcome.planInOrder().isEmpty());
    }

    /** 池子超上限时只撮合前 N 单：待派池积压不得把一次 tick 的耗时顶起来。 */
    @Test
    void poolIsCappedByConfig() {
        properties.setBatchMaxPoolSize(2);
        when(assignService.rankCandidatesForBatch(any())).thenAnswer(invocation -> List.of(
                candidate(10L, "ZJF-AV-01", 100D), candidate(11L, "ZJF-AV-02", 200D)));
        DispatchBatchAssignService.BatchAssignOutcome outcome =
                batchService.assignOrders(List.of(order(1L), order(2L), order(3L)));
        assertEquals(2, outcome.orders());
        assertEquals(2, outcome.matched());
    }

    private static OrderEntity order(Long id) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        return order;
    }

    private static RankedCandidate candidate(Long vehicleId, String code, double totalScore) {
        return new RankedCandidate(new DecisionInput.RankedCandidateIdentity(vehicleId, code),
                totalScore, 0D, 0D, 0D, 1D, totalScore);
    }
}
