package com.fsd.dispatch.dispatch;

import com.fsd.dispatch.config.DispatchPolicyProperties;
import com.fsd.dispatch.core.AssignmentSolver;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.metrics.DispatchDecisionMetrics;
import com.fsd.order.entity.OrderEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 待派池批量撮合（§2.3）。
 *
 * <p>成本矩阵的行是订单、列是车辆，元素是该单在该车上的<b>在位策略总分</b>（越小越优），
 * 不可行对用有限大数 {@link #INFEASIBLE} 挡掉 —— 用 {@code POSITIVE_INFINITY} 会让"行多列少"时
 * 的可行度判定失真，也拿不到"这单根本没配上"的信号（{@link AssignmentSolver} 的入参约定）。
 *
 * <p>两条设计约束：
 * <ol>
 *   <li><b>只出顺序，不做派车</b>。矩阵回答"谁先占哪台车"，真正的可行性、SOC 链校验、MAPF 预约
 *       仍由逐单链路判定（{@link DispatchVehicleAssignService#rankCandidatesForBatch} 与派单共用同一套
 *       漏斗，所以矩阵不会给出"矩阵可行、派单不可行"的第二真相）。批量相对贪心的实际收益来源是
 *       <b>提交顺序由全局配对决定</b>，而不是另起一套派车逻辑。</li>
 *   <li><b>贪心是常驻 fallback</b>：配置切到 {@code GREEDY}、矩阵为空、或匈牙利抛错时，
 *       都退到同一份矩阵上的贪心基线，而不是整批不派。</li>
 * </ol>
 */
@Service
public class DispatchBatchAssignServiceImpl implements DispatchBatchAssignService {

    private static final Logger log = LoggerFactory.getLogger(DispatchBatchAssignServiceImpl.class);

    /** 不可行对的有限大数代价（§2.3）：远大于任何真实总分，又不至于让求解器溢出。 */
    private static final double INFEASIBLE = 1_000_000_000_000D;
    /** 配对分数达到该阈值即视为"这单在矩阵里根本没配到可行车"。 */
    private static final double FEASIBLE_MAX = INFEASIBLE / 10D;

    private final DispatchVehicleAssignService assignService;
    private final DispatchPolicyProperties properties;
    private final DispatchDecisionMetrics metrics;

    public DispatchBatchAssignServiceImpl(DispatchVehicleAssignService assignService,
                                          DispatchPolicyProperties properties,
                                          DispatchDecisionMetrics metrics) {
        this.assignService = assignService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public BatchAssignOutcome assignOrders(List<OrderEntity> pendingOrders) {
        long startedAt = System.nanoTime();
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return new BatchAssignOutcome("GREEDY", 0, 0, 0, 0D, 0D, null, Map.of());
        }
        int limit = properties.getBatchMaxPoolSize();
        List<OrderEntity> pool = pendingOrders.size() <= limit
                ? pendingOrders : new ArrayList<>(pendingOrders.subList(0, limit));

        List<List<RankedCandidate>> rankedPerOrder = new ArrayList<>(pool.size());
        Set<Long> vehicleIdSet = new LinkedHashSet<>();
        Map<Long, String> vehicleCodeById = new LinkedHashMap<>();
        for (OrderEntity order : pool) {
            List<RankedCandidate> ranked = assignService.rankCandidatesForBatch(order);
            rankedPerOrder.add(ranked);
            for (RankedCandidate candidate : ranked) {
                if (candidate.vehicleId() != null) {
                    vehicleIdSet.add(candidate.vehicleId());
                    vehicleCodeById.putIfAbsent(candidate.vehicleId(), candidate.vehicleCode());
                }
            }
        }
        List<Long> vehicleColumns = new ArrayList<>(vehicleIdSet);
        double[][] cost = buildCostMatrix(rankedPerOrder, vehicleColumns);

        Plan greedy = solveGreedy(cost);
        Plan hungarian = "HUNGARIAN".equals(properties.getBatchAlgorithm()) ? solveHungarian(cost) : null;
        Plan chosen = hungarian != null ? hungarian : greedy;
        String algorithm = hungarian != null ? "HUNGARIAN" : "GREEDY";

        // 配对按分数升序排出提交顺序：全局最划算的对先占车 —— 这就是"批量"相对"按到达顺序贪心"的差别
        Map<Long, String> planInOrder = new LinkedHashMap<>();
        for (Pair pair : chosen.pairs().stream().sorted(Comparator.comparingDouble(Pair::cost)).toList()) {
            OrderEntity order = pool.get(pair.row());
            String vehicleCode = vehicleCodeById.get(pair.columnVehicle(vehicleColumns));
            if (order.getId() != null && vehicleCode != null) {
                planInOrder.put(order.getId(), vehicleCode);
            }
        }

        // 配对数不同 ⇒ 两侧总量不可比（省了分却少派了单不是收益），此时宁可不报节省量
        Double savings = chosen.matched() == greedy.matched()
                ? greedy.totalScore() - chosen.totalScore() : null;
        metrics.recordBatchResult("orders", pool.size());
        metrics.recordBatchResult("matched", chosen.matched());
        metrics.recordBatchResult(algorithm.equals("HUNGARIAN") ? "hungarian" : "greedy_fallback", chosen.matched());
        metrics.recordBatchDuration(Duration.ofNanos(System.nanoTime() - startedAt));
        metrics.recordBatchSavings(savings == null ? 0D : savings);

        return new BatchAssignOutcome(algorithm, pool.size(), chosen.matched(), greedy.matched(),
                chosen.totalScore(), greedy.totalScore(), savings, planInOrder);
    }

    /** 逐单贪心：按池内顺序，每单取当前未被占用、分数最优的可行车。 */
    private Plan solveGreedy(double[][] cost) {
        int columns = cost.length == 0 ? 0 : cost[0].length;
        boolean[] vehicleTaken = new boolean[columns];
        List<Pair> pairs = new ArrayList<>();
        double total = 0D;
        for (int row = 0; row < cost.length; row++) {
            int bestCol = -1;
            double bestCost = FEASIBLE_MAX;
            for (int col = 0; col < cost[row].length; col++) {
                if (vehicleTaken[col] || cost[row][col] >= FEASIBLE_MAX) {
                    continue;
                }
                if (cost[row][col] < bestCost) {
                    bestCost = cost[row][col];
                    bestCol = col;
                }
            }
            if (bestCol >= 0) {
                vehicleTaken[bestCol] = true;
                pairs.add(new Pair(row, bestCol, bestCost));
                total += bestCost;
            }
        }
        return new Plan(pairs, total);
    }

    /** @return 匈牙利解；矩阵不可解（形状/异常）时返回 null，由调用方落回贪心 */
    private Plan solveHungarian(double[][] cost) {
        if (cost.length == 0 || cost[0].length == 0) {
            return null;
        }
        try {
            boolean transposed = cost.length > cost[0].length;
            int[] solution = AssignmentSolver.hungarian(transposed ? transpose(cost) : cost);
            List<Pair> pairs = new ArrayList<>();
            double total = 0D;
            for (int index = 0; index < solution.length; index++) {
                int partner = solution[index];
                if (partner < 0) {
                    continue;
                }
                int row = transposed ? partner : index;
                int col = transposed ? index : partner;
                if (row >= cost.length || col >= cost[row].length || cost[row][col] >= FEASIBLE_MAX) {
                    continue;
                }
                pairs.add(new Pair(row, col, cost[row][col]));
                total += cost[row][col];
            }
            return new Plan(pairs, total);
        } catch (RuntimeException ex) {
            // 求解失败不能把整批卡住：留在贪心路径上，降级本身进指标与 WARN
            metrics.recordPolicyFallback("HUNGARIAN");
            log.warn("batch matching fell back to greedy: {}", ex.toString());
            return null;
        }
    }

    /** rows=订单、cols=车辆；缺项（该车对该单不可行/不可达）填 {@link #INFEASIBLE}。 */
    private static double[][] buildCostMatrix(List<List<RankedCandidate>> rankedPerOrder, List<Long> vehicleColumns) {
        double[][] cost = new double[rankedPerOrder.size()][Math.max(vehicleColumns.size(), 1)];
        for (int row = 0; row < cost.length; row++) {
            Arrays.fill(cost[row], INFEASIBLE);
            for (RankedCandidate candidate : rankedPerOrder.get(row)) {
                int col = candidate.vehicleId() == null ? -1 : vehicleColumns.indexOf(candidate.vehicleId());
                if (col >= 0) {
                    cost[row][col] = candidate.totalScore();
                }
            }
        }
        return cost;
    }

    private static double[][] transpose(double[][] matrix) {
        double[][] out = new double[matrix[0].length][matrix.length];
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                out[col][row] = matrix[row][col];
            }
        }
        return out;
    }

    /** 一个配对：{@code row} 是订单下标，{@code col} 是车辆列下标。 */
    private record Pair(int row, int col, double cost) {

        Long columnVehicle(List<Long> vehicleColumns) {
            return col < vehicleColumns.size() ? vehicleColumns.get(col) : null;
        }
    }

    private record Plan(List<Pair> pairs, double totalScore) {

        int matched() {
            return pairs.size();
        }
    }
}
