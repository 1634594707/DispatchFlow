package com.fsd.dispatch.service;

import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ParkRoutePlannerService {

    List<ParkPointResponse> buildRoute(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);

    /**
     * Returns false when start/end cannot be connected on the park road graph (P2-11).
     */
    boolean isReachable(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);

    ParkRoadGraph loadGraph(Long parkId);

    /**
     * 当前使用中的路网图版本指纹，供决策快照记录"这一单看到的是哪一版路网"。
     * 无缓存实现返回 null。
     */
    default String graphVersion(Long parkId) {
        return null;
    }

    /** 丢弃缓存，下一次规划重新从库里全量建图。围栏/路网变更后调用可立即生效。 */
    default void invalidateGraphCache() {
    }

    /**
     * §M5 候选预筛：返回"沿有向路网送得到 (endX,endY) 这个位置"的节点集合。
     *
     * <p>算法是从目标点的最近节点出发，沿 {@link ParkRoadGraph#reverseAdjacency()} 做一次 BFS。
     * 一次 O(V+E) 顶得上"每台候选车各跑一次 A*"——扩范围后候选 20 台、图 430 节点时，
     * 省掉的是十几次全图搜索。
     *
     * <p>刻意只用于**剪枝**：真正的可达性判定仍由 {@link #isReachable} 在循环里做。
     * 两者口径必须一致，所以这里复用 {@link ParkRoadGraph#nearestNodeCode}（与寻路同一把尺）。
     *
     * @return 空集表示"不适用"（图还没建或找不到落点），调用方此时**不应剪枝**，
     *         以保留 {@code isReachable} 在空图上放行的旧语义。
     */
    default java.util.Set<String> nodesThatCanReach(Long parkId, BigDecimal endX, BigDecimal endY) {
        ParkRoadGraph graph = loadGraph(parkId);
        if (graph == null || graph.isEmpty()) {
            return java.util.Set.of();
        }
        String target = graph.nearestNodeCode(endX, endY);
        if (target == null) {
            return java.util.Set.of();
        }
        Map<String, List<String>> reverse = graph.reverseAdjacency();
        java.util.Set<String> canReach = new java.util.HashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        canReach.add(target);
        queue.add(target);
        while (!queue.isEmpty()) {
            for (String from : reverse.getOrDefault(queue.poll(), List.of())) {
                if (canReach.add(from)) {
                    queue.add(from);
                }
            }
        }
        return canReach;
    }

    List<String> shortestNodePathWithPenalties(ParkRoadGraph graph,
                                               BigDecimal startX, BigDecimal startY,
                                               BigDecimal endX, BigDecimal endY,
                                               Map<String, Double> edgePenalties);

    List<ParkPointResponse> buildRouteFromNodePath(ParkRoadGraph graph,
                                                   BigDecimal startX, BigDecimal startY,
                                                   BigDecimal endX, BigDecimal endY,
                                                   List<String> nodePath);
}
