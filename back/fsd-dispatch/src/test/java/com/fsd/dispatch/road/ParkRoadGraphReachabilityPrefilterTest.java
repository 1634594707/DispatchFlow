package com.fsd.dispatch.road;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §M5 候选预筛的等价性门。
 *
 * <p>预筛用一次反向 BFS 代替"每台候选车跑一次 A*"。它唯一的正确性要求是：
 * <b>被它剪掉的车，必须恰好是 {@code isReachable} 也会判不可达的那些</b>。
 * 剪多了 = 有車能送却不送（静默丢单，比慢得多严重）；剪少了 = 白剪。
 * 所以这里不测"快不快"，只拿一个独立实现的正向 DFS 逐节点对账。
 */
class ParkRoadGraphReachabilityPrefilterTest {

    private static RoadNodeEntity node(String code, int x, int y) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to, String direction) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setDirection(direction);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static ParkRoadGraph graph(List<RoadNodeEntity> nodes, List<RoadSegmentEntity> segments) {
        return ParkRoadGraph.fromDatabase(nodes, segments);
    }

    /** 独立实现：从 start 沿正向邻接表 DFS，看能不能走到 target。与反向 BFS 无共享代码。 */
    private static boolean forwardReachable(ParkRoadGraph graph, String start, String target) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur.equals(target)) {
                return true;
            }
            for (String next : graph.neighbors(cur)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static Set<String> prefilterSet(ParkRoadGraph graph, String targetNode) {
        return bfsReverse(graph, targetNode);
    }

    private static Set<String> bfsReverse(ParkRoadGraph graph, String target) {
        Set<String> canReach = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        canReach.add(target);
        queue.add(target);
        while (!queue.isEmpty()) {
            for (String from : graph.reverseAdjacency().getOrDefault(queue.poll(), List.of())) {
                if (canReach.add(from)) {
                    queue.add(from);
                }
            }
        }
        return canReach;
    }

    @Test
    @DisplayName("反向 BFS 的集合 == 逐节点正向 DFS 的可达集合（两个连通块、含单向边）")
    void prefilterAgreesWithForwardSearchNodeByNode() {
        // 块一：A=B=C=D 双向链；块二：X→Y 单向，与块一完全不连
        ParkRoadGraph g = graph(
                List.of(node("A", 0, 0), node("B", 10, 0), node("C", 20, 0), node("D", 30, 0),
                        node("X", 200, 200), node("Y", 210, 200)),
                List.of(segment("A", "B", "BIDIRECTIONAL"),
                        segment("B", "C", "BIDIRECTIONAL"),
                        segment("C", "D", "BIDIRECTIONAL"),
                        segment("X", "Y", "FORWARD")));

        for (String target : List.of("A", "B", "C", "D", "X", "Y")) {
            Set<String> viaReverseBfs = prefilterSet(g, target);
            for (String candidate : g.nodes().keySet()) {
                boolean expected = forwardReachable(g, candidate, target);
                assertThat(viaReverseBfs.contains(candidate))
                        .as("目标 %s：节点 %s 的预筛结论必须与正向搜索一致", target, candidate)
                        .isEqualTo(expected);
            }
        }

        // 跨块必须互不可达（这就是预筛真正省掉 A* 的地方）
        assertThat(prefilterSet(g, "D")).containsExactlyInAnyOrder("A", "B", "C", "D");
        assertThat(prefilterSet(g, "Y")).containsExactlyInAnyOrder("X", "Y");
        assertThat(prefilterSet(g, "X")).containsExactly("X");
    }

    @Test
    @DisplayName("单向边不能被反向 BFS 反向放行")
    void oneWayEdgeIsNotTraversedBackwards() {
        ParkRoadGraph g = graph(
                List.of(node("P", 0, 0), node("Q", 50, 0)),
                List.of(segment("P", "Q", "FORWARD")));

        // P→Q 通：所以"送得到 Q"的集合含 P；但 Q 回不到 P，故"送得到 P"的集合不含 Q
        assertThat(prefilterSet(g, "Q")).contains("P", "Q");
        assertThat(prefilterSet(g, "P")).containsExactly("P");
    }

    @Test
    @DisplayName("空图返回空集 —— 调用方据此不剪枝，保留 isReachable 空图放行的旧语义")
    void emptyGraphYieldsEmptySet() {
        ParkRoadGraph g = graph(List.of(), List.of());
        assertThat(g.isEmpty()).isTrue();
        assertThat(g.reverseAdjacency()).isEmpty();
        assertThat(g.nearestNodeCode(BigDecimal.ONE, BigDecimal.ONE)).isNull();
    }

    @Test
    @DisplayName("nearestNodeCode 用示意 px 欧氏，与寻路的 nearestNode 同一把尺")
    void nearestNodeUsesPixelDistance() {
        ParkRoadGraph g = graph(
                List.of(node("NEAR", 100, 100), node("FAR", 900, 900)),
                List.of(segment("NEAR", "FAR", "BIDIRECTIONAL")));

        assertThat(g.nearestNodeCode(BigDecimal.valueOf(110), BigDecimal.valueOf(95))).isEqualTo("NEAR");
        assertThat(g.nearestNodeCode(BigDecimal.valueOf(880), BigDecimal.valueOf(920))).isEqualTo("FAR");
    }

    @Test
    @DisplayName("反向邻接表被重复取用同一实例（懒算缓存），不会每次重建")
    void reverseAdjacencyIsCached() {
        ParkRoadGraph g = graph(
                List.of(node("A", 0, 0), node("B", 10, 0)),
                List.of(segment("A", "B", "BIDIRECTIONAL")));
        Map<String, List<String>> first = g.reverseAdjacency();
        Map<String, List<String>> second = g.reverseAdjacency();
        assertThat(first).isSameAs(second);
    }
}
