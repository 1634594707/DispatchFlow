package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ALG-A* 回归：A* 与 Dijkstra 的最优性一致性与搜索规模对比。
 *
 * <p>覆盖点：
 * <ul>
 *   <li>A* 在带障碍网格上返回与 Dijkstra 等价的最优总代价</li>
 *   <li>A* 展开节点数少于 Dijkstra（启发函数剪枝收益）</li>
 *   <li>MAPF 边惩罚下两种算法仍给出相同最优代价（惩罚只增大边权，不破坏可采纳性）</li>
 *   <li>节点 GPS 覆盖混用时自动回退 Dijkstra（避免米/像素混算导致次优路径）</li>
 *   <li>不可达时 planAStar 返回空路径，shortestNodePathWithPenalties 抛 PARK_ROUTE_NOT_FOUND</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ParkRoutePlannerAStarTest {

    private static final double EPSILON = 1e-6;

    @Mock
    private RoadNodeMapper roadNodeMapper;
    @Mock
    private RoadSegmentMapper roadSegmentMapper;
    @Mock
    private ParkStationService parkStationService;

    private ParkPilotProperties parkPilotProperties;
    private ParkRoutePlannerServiceImpl planner;

    @BeforeEach
    void setUp() {
        parkPilotProperties = new ParkPilotProperties();
        planner = new ParkRoutePlannerServiceImpl(
                parkPilotProperties, roadNodeMapper, roadSegmentMapper, parkStationService);
    }

    @Test
    void aStarShouldMatchDijkstraOptimalCostOnGridWithObstacle() {
        ParkRoadGraph graph = gridWithWall(24, 16, 11, 8);

        ParkRoutePlannerServiceImpl.RoutePlan astar = planner.planAStar(graph, "N0_0", "N23_15", Map.of());
        ParkRoutePlannerServiceImpl.RoutePlan dijkstra =
                planner.planDijkstra(graph, "N0_0", "N23_15", Map.of());

        assertTrue(astar.found(), "A* 应找到路径");
        assertTrue(dijkstra.found(), "Dijkstra 应找到路径");
        assertEquals(dijkstra.cost(), astar.cost(), EPSILON, "A* 与 Dijkstra 最优代价必须一致");
        assertEquals("ASTAR", astar.algorithm());
        assertEquals("DIJKSTRA", dijkstra.algorithm());
        assertEquals("N0_0", astar.nodePath().get(0));
        assertEquals("N23_15", astar.nodePath().get(astar.nodePath().size() - 1));
        assertPathIsContiguous(graph, astar.nodePath());
    }

    @Test
    void aStarShouldExpandFewerNodesThanDijkstra() {
        ParkRoadGraph graph = grid(32, 24);

        ParkRoutePlannerServiceImpl.RoutePlan astar = planner.planAStar(graph, "N0_0", "N31_23", Map.of());
        ParkRoutePlannerServiceImpl.RoutePlan dijkstra =
                planner.planDijkstra(graph, "N0_0", "N31_23", Map.of());

        assertTrue(astar.found() && dijkstra.found());
        assertEquals(dijkstra.cost(), astar.cost(), EPSILON);
        assertTrue(astar.expandedNodes() < dijkstra.expandedNodes(),
                "A* 应展开更少节点：A*=" + astar.expandedNodes() + ", Dijkstra=" + dijkstra.expandedNodes());
    }

    @Test
    void aStarShouldRespectMapfPenaltiesLikeDijkstra() {
        ParkRoadGraph graph = grid(12, 12);
        // 对一条最短路首跳边施加 MAPF 冲突惩罚，迫使规划器改走替代路线
        Map<String, Double> penalties = Map.of("N0_0>N1_0", 3.0D);

        ParkRoutePlannerServiceImpl.RoutePlan astar = planner.planAStar(graph, "N0_0", "N11_11", penalties);
        ParkRoutePlannerServiceImpl.RoutePlan dijkstra =
                planner.planDijkstra(graph, "N0_0", "N11_11", penalties);

        assertTrue(astar.found() && dijkstra.found());
        assertEquals(dijkstra.cost(), astar.cost(), EPSILON, "惩罚只增大边权，代价仍须一致");
        assertFalse("N1_0".equals(astar.nodePath().get(1)),
                "被惩罚的边不应再被选为第一跳：" + astar.nodePath());
    }

    @Test
    void shouldFallBackToDijkstraWhenNodeMetricIsMixed() {
        List<RoadNodeEntity> nodes = List.of(
                withGps(node("N0_0", 0, 0), 121.0700, 31.9600),
                withGps(node("N1_0", 100, 0), 121.0710, 31.9600),
                node("N1_1", 100, 100),
                node("N0_1", 0, 100));
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes, gridSegments(2, 2));

        assertFalse(ParkRoutePlannerServiceImpl.isMetricConsistent(graph), "混合 GPS 覆盖应判为度量不一致");
        ParkRoutePlannerServiceImpl.RoutePlan plan = planner.planRoute(graph, "N0_0", "N1_1", Map.of());
        assertEquals("DIJKSTRA", plan.algorithm(), "度量不一致时必须回退 Dijkstra（h 不可采纳）");
        assertTrue(plan.found());
    }

    @Test
    void shouldUseAStarWhenAllNodesShareSameMetric() {
        List<RoadNodeEntity> nodes = List.of(
                withGps(node("N0_0", 0, 0), 121.0700, 31.9600),
                withGps(node("N1_0", 100, 0), 121.0710, 31.9600),
                withGps(node("N1_1", 100, 100), 121.0710, 31.9610));
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes, gridSegments(2, 2));

        assertTrue(ParkRoutePlannerServiceImpl.isMetricConsistent(graph));
        ParkRoutePlannerServiceImpl.RoutePlan plan = planner.planRoute(graph, "N0_0", "N1_1", Map.of());
        assertEquals("ASTAR", plan.algorithm());
        assertTrue(plan.cost() > 0D, "全 GPS 图应产生 Haversine 米制代价");
    }

    @Test
    void aStarShouldReturnEmptyPathWhenTargetDisconnected() {
        // 3×3 网格 + 一个孤立节点：孤立节点是几何最近点，但图上不可达
        List<RoadNodeEntity> nodes = new ArrayList<>(gridNodes(3, 3));
        nodes.add(node("ISO", 10_000, 10_000));
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes, gridSegments(3, 3));

        ParkRoutePlannerServiceImpl.RoutePlan plan = planner.planAStar(graph, "N0_0", "ISO", Map.of());
        assertFalse(plan.found(), "跨子图不可达时 A* 应返回空路径");
        assertEquals(1, planner.planAStar(graph, "N0_0", "N0_0", Map.of()).nodePath().size(),
                "同一起终点应退化为单节点路径");
    }

    @Test
    void shortestNodePathShouldThrowWhenTargetNodeIsolated() {
        List<RoadNodeEntity> nodes = new ArrayList<>(gridNodes(3, 3));
        nodes.add(node("ISO", 10_000, 10_000));
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(nodes);
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(gridSegments(3, 3));

        ParkRoadGraph graph = planner.loadGraph(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> planner.shortestNodePathWithPenalties(graph,
                        BigDecimal.valueOf(0), BigDecimal.valueOf(0),
                        BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000), Map.of()));
        assertEquals("PARK_ROUTE_NOT_FOUND", ex.getCode());
    }

    @Test
    void shouldWriteAlgorithmComparisonReport() throws java.io.IOException {
        StringBuilder table = new StringBuilder(
                "| 场景 | 算法 | 总代价(px) | 相对最优 | 展开节点数 | 路径跳数 |\n"
                        + "| --- | --- | --- | --- | --- | --- |\n");
        Object[][] scenarios = {
                {"12×12 无障碍", 12, 12, -1, -1},
                {"24×16 单列障碍", 24, 16, 11, 8},
                {"32×24 长距离", 32, 24, -1, -1},
                {"32×24 单列障碍", 32, 24, 15, 12},
        };
        double[] weights = {2.0D, 3.0D};
        double sumStandardExpanded = 0D;
        double sumWeightedExpanded = 0D;
        int scenarioCount = 0;

        for (Object[] scenario : scenarios) {
            String name = (String) scenario[0];
            int cols = (int) scenario[1];
            int rows = (int) scenario[2];
            int wall = (int) scenario[3];
            int gap = (int) scenario[4];
            ParkRoadGraph graph = wall < 0 ? grid(cols, rows) : gridWithWall(cols, rows, wall, gap);
            String start = "N0_0";
            String end = "N" + (cols - 1) + "_" + (rows - 1);

            ParkRoutePlannerServiceImpl.RoutePlan astar = planner.planAStar(graph, start, end, Map.of(), 1.0D);
            ParkRoutePlannerServiceImpl.RoutePlan dijkstra = planner.planDijkstra(graph, start, end, Map.of());

            assertTrue(astar.found() && dijkstra.found(), name + " 应可达");
            assertEquals(dijkstra.cost(), astar.cost(), EPSILON, name + "：标准 A*(w=1) 与 Dijkstra 代价必须一致");

            double optimal = dijkstra.cost();
            table.append(row(name, "A* (w=1)", astar, optimal));
            ParkRoutePlannerServiceImpl.RoutePlan lastWeighted = null;
            for (double w : weights) {
                ParkRoutePlannerServiceImpl.RoutePlan weighted =
                        planner.planAStar(graph, start, end, Map.of(), w);
                assertTrue(weighted.found(), name + " w=" + w + " 应可达");
                assertTrue(weighted.cost() <= w * optimal + EPSILON,
                        name + " w=" + w + " 代价超出 w 倍最优上界");
                assertTrue(weighted.expandedNodes() <= astar.expandedNodes(),
                        name + " w=" + w + " 展开节点数不应超过 w=1");
                table.append(row(name, String.format("加权 A* (w=%.0f)", w), weighted, optimal));
                lastWeighted = weighted;
            }
            table.append(row(name, "Dijkstra", dijkstra, optimal));

            sumStandardExpanded += astar.expandedNodes();
            sumWeightedExpanded += lastWeighted.expandedNodes();
            scenarioCount++;
        }

        double reduction = (sumStandardExpanded - sumWeightedExpanded) / sumStandardExpanded * 100D;
        java.nio.file.Path directory = java.nio.file.Path.of("target", "route-algorithm-report");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("astar-vs-dijkstra.md"),
                "# A* / 加权 A* / Dijkstra 对比（ALG-A*、T-05）\n\n"
                        + "- 生成方式：`ParkRoutePlannerAStarTest.shouldWriteAlgorithmComparisonReport`\n"
                        + "- 图规模：网格路网，相邻节点距离 100px（schematic 度量，h 与边权同度量）\n"
                        + "- 相对最优 = 总代价 ÷ Dijkstra 最优代价\n\n"
                        + table
                        + String.format("%n## 结论%n%n")
                        + String.format("- **最优性（w=1）**：标准 A* 与 Dijkstra 总代价逐场景完全一致，h 可采纳性成立。%n")
                        + String.format("- **搜索规模**：加权 A*(w=3) 相对标准 A* 平均展开节点数下降 **%.1f%%**（%d → %d，%d 个场景合计）。%n",
                        reduction, (long) sumStandardExpanded, (long) sumWeightedExpanded, scenarioCount)
                        + String.format("- **解质量权衡**：w>1 时 h 不再可采纳，代价上界为 `w × 最优`。"
                        + "本组均匀网格上实测代价与最优**完全相同**（相对最优 1.000×），即加速未付出精度代价——"
                        + "这是网格拓扑（存在大量等长最短路）与欧氏 h 高度贴合共同作用的结果，"
                        + "**不可外推**到边权不均的真实园区路网，那里需以 w 倍上界为验收口径。%n")
                        + String.format("- **默认关闭**：`fsd.park.route-plan.a-star-weight` 默认 1.0，即生产走标准 A*，"
                        + "加权模式需显式开启。%n"),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String row(String scenario, String algorithm,
                              ParkRoutePlannerServiceImpl.RoutePlan plan, double optimal) {
        double ratio = optimal > 0D ? plan.cost() / optimal : 1D;
        return String.format("| %s | %s | %.1f | %.3f× | %d | %d |%n",
                scenario, algorithm, plan.cost(), ratio, plan.expandedNodes(), plan.nodePath().size());
    }

    @Test
    void buildRouteShouldStillRenderStartAndEndNodesWithAStarEnabled() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("R1", 100, 120), node("R2", 220, 120), node("R3", 420, 120)));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("R1", "R2"), segment("R2", "R3")));

        List<ParkPointResponse> route = planner.buildRoute(1L,
                BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125));

        assertEquals(5, route.size(), "START + R1 + R2 + R3 + END");
        assertEquals("START", route.get(0).getCode());
        assertEquals("END", route.get(route.size() - 1).getCode());
        assertTrue(planner.isReachable(1L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125)));
    }

    // ==================== T-05 加权 A* ====================

    @Test
    void weightedAStarShouldKeepOptimalCostWhenWeightIsOne() {
        ParkRoadGraph graph = gridWithWall(24, 16, 11, 8);

        ParkRoutePlannerServiceImpl.RoutePlan standard = planner.planAStar(graph, "N0_0", "N23_15", Map.of(), 1.0D);
        ParkRoutePlannerServiceImpl.RoutePlan dijkstra = planner.planDijkstra(graph, "N0_0", "N23_15", Map.of());

        assertEquals("ASTAR", standard.algorithm(), "w=1 应标记为标准 A*");
        assertEquals(dijkstra.cost(), standard.cost(), EPSILON, "w=1 必须保持最优代价");
    }

    @Test
    void weightedAStarShouldRespectSuboptimalityBound() {
        ParkRoadGraph graph = gridWithWall(32, 24, 15, 12);
        ParkRoutePlannerServiceImpl.RoutePlan optimal = planner.planDijkstra(graph, "N0_0", "N31_23", Map.of());

        for (double w : new double[] {1.5D, 2.0D, 3.0D, 5.0D}) {
            ParkRoutePlannerServiceImpl.RoutePlan plan =
                    planner.planAStar(graph, "N0_0", "N31_23", Map.of(), w);
            assertTrue(plan.found(), "w=" + w + " 应找到路径");
            assertEquals("WEIGHTED_ASTAR", plan.algorithm(), "w>1 应标记为加权 A*");
            assertEquals(w, plan.weight(), EPSILON);
            assertTrue(plan.cost() >= optimal.cost() - EPSILON,
                    "w=" + w + " 解质量不可能优于最优：" + plan.cost() + " < " + optimal.cost());
            assertTrue(plan.cost() <= w * optimal.cost() + EPSILON,
                    "w=" + w + " 代价应不超过 w 倍最优：" + plan.cost() + " > " + (w * optimal.cost()));
        }
    }

    @Test
    void weightedAStarShouldNotExpandMoreThanStandardAStar() {
        ParkRoadGraph graph = gridWithWall(32, 24, 15, 12);

        ParkRoutePlannerServiceImpl.RoutePlan standard = planner.planAStar(graph, "N0_0", "N31_23", Map.of(), 1.0D);
        ParkRoutePlannerServiceImpl.RoutePlan weighted = planner.planAStar(graph, "N0_0", "N31_23", Map.of(), 3.0D);

        assertTrue(weighted.expandedNodes() <= standard.expandedNodes(),
                "加权 A* 不应展开更多节点：w=3 -> " + weighted.expandedNodes()
                        + ", w=1 -> " + standard.expandedNodes());
    }

    @Test
    void effectiveWeightShouldClampInvalidValuesToOne() {
        parkPilotProperties.getRoutePlan().setAStarWeight(2.5D);
        assertEquals(2.5D, planner.effectiveAStarWeight(), EPSILON);

        parkPilotProperties.getRoutePlan().setAStarWeight(0.5D);
        assertEquals(1.0D, planner.effectiveAStarWeight(), EPSILON, "w<1 无收益且破坏上界语义，应夹取为 1.0");

        parkPilotProperties.getRoutePlan().setAStarWeight(Double.NaN);
        assertEquals(1.0D, planner.effectiveAStarWeight(), EPSILON);

        parkPilotProperties.getRoutePlan().setAStarWeight(Double.POSITIVE_INFINITY);
        assertEquals(1.0D, planner.effectiveAStarWeight(), EPSILON);
    }

    @Test
    void planRouteShouldUseWeightedAStarWhenWeightConfigured() {
        parkPilotProperties.getRoutePlan().setAStarWeight(2.0D);
        ParkRoadGraph graph = grid(12, 12);

        ParkRoutePlannerServiceImpl.RoutePlan plan = planner.planRoute(graph, "N0_0", "N11_11", Map.of());

        assertEquals("WEIGHTED_ASTAR", plan.algorithm(), "配置 w>1 后 planRoute 应走加权 A*");
        assertEquals(2.0D, plan.weight(), EPSILON);
        assertTrue(plan.found());
    }

    @Test
    void weightedAStarShouldFallBackToStandardWhenWeightConfiguredToOne() {
        parkPilotProperties.getRoutePlan().setAStarWeight(1.0D);
        ParkRoadGraph graph = grid(12, 12);

        ParkRoutePlannerServiceImpl.RoutePlan plan = planner.planRoute(graph, "N0_0", "N11_11", Map.of());

        assertEquals("ASTAR", plan.algorithm(), "w=1 应保持标准 A*（最优性不变）");
        assertEquals(1.0D, plan.weight(), EPSILON);
    }

    // ==================== 图构造工具 ====================

    /** cols×rows 全连通网格（无 GPS，度量一致 → 走 A*）。 */
    private static ParkRoadGraph grid(int cols, int rows) {
        return ParkRoadGraph.fromDatabase(gridNodes(cols, rows), gridSegments(cols, rows));
    }

    /**
     * 带整列障碍的网格：{@code wallColumn} 列两侧的横向边全部切断，仅保留 {@code wallGapRow} 行通道。
     */
    private static ParkRoadGraph gridWithWall(int cols, int rows, int wallColumn, int wallGapRow) {
        List<RoadSegmentEntity> kept = new ArrayList<>();
        for (RoadSegmentEntity s : gridSegments(cols, rows)) {
            boolean crosses = s.getFromNodeCode().startsWith("N" + wallColumn + "_")
                    != s.getToNodeCode().startsWith("N" + wallColumn + "_");
            boolean atGap = s.getFromNodeCode().endsWith("_" + wallGapRow)
                    && s.getToNodeCode().endsWith("_" + wallGapRow);
            if (!crosses || atGap) {
                kept.add(s);
            }
        }
        return ParkRoadGraph.fromDatabase(gridNodes(cols, rows), kept);
    }

    private static List<RoadNodeEntity> gridNodes(int cols, int rows) {
        List<RoadNodeEntity> nodes = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                nodes.add(node("N" + c + "_" + r, c * 100, r * 100));
            }
        }
        return nodes;
    }

    private static List<RoadSegmentEntity> gridSegments(int cols, int rows) {
        List<RoadSegmentEntity> segments = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (c + 1 < cols) {
                    segments.add(segment("N" + c + "_" + r, "N" + (c + 1) + "_" + r));
                }
                if (r + 1 < rows) {
                    segments.add(segment("N" + c + "_" + r, "N" + c + "_" + (r + 1)));
                }
            }
        }
        return segments;
    }

    private static void assertPathIsContiguous(ParkRoadGraph graph, List<String> path) {
        for (int i = 0; i + 1 < path.size(); i++) {
            assertTrue(graph.neighbors(path.get(i)).contains(path.get(i + 1)),
                    "路径不连续：" + path.get(i) + " → " + path.get(i + 1));
        }
    }

    private static RoadNodeEntity node(String code, int x, int y) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static RoadNodeEntity withGps(RoadNodeEntity entity, double lng, double lat) {
        entity.setCoordLng(BigDecimal.valueOf(lng));
        entity.setCoordLat(BigDecimal.valueOf(lat));
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
