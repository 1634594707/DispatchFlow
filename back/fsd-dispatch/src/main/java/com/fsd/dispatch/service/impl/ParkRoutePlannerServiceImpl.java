package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ParkRoutePlannerServiceImpl implements ParkRoutePlannerService {

    /** 寻路算法标识（ALG-A*），用于日志、测试与规模对比。 */
    static final String ALGORITHM_ASTAR = "ASTAR";
    static final String ALGORITHM_WEIGHTED_ASTAR = "WEIGHTED_ASTAR";
    static final String ALGORITHM_DIJKSTRA = "DIJKSTRA";
    static final String ALGORITHM_TRIVIAL = "TRIVIAL";

    private final ParkPilotProperties parkPilotProperties;
    private final RoadNodeMapper roadNodeMapper;
    private final RoadSegmentMapper roadSegmentMapper;
    private final ParkStationService parkStationService;

    public ParkRoutePlannerServiceImpl(ParkPilotProperties parkPilotProperties,
                                       RoadNodeMapper roadNodeMapper,
                                       RoadSegmentMapper roadSegmentMapper,
                                       ParkStationService parkStationService) {
        this.parkPilotProperties = parkPilotProperties;
        this.roadNodeMapper = roadNodeMapper;
        this.roadSegmentMapper = roadSegmentMapper;
        this.parkStationService = parkStationService;
    }

    @Override
    public List<ParkPointResponse> buildRoute(Long parkId, BigDecimal startX, BigDecimal startY,
                                              BigDecimal endX, BigDecimal endY) {
        if (startX == null || startY == null || endX == null || endY == null) {
            return List.of();
        }
        ParkRoadGraph graph = resolveGraph(parkId);
        if (graph.isEmpty()) {
            // Phase 4：空路网不再返回直线，直接抛异常提示派单失败
            throw new BusinessException("PARK_ROAD_NETWORK_EMPTY",
                    "园区路网数据为空，无法规划路径，请先配置路网节点和路段");
        }

        String startNode = nearestNode(graph, startX, startY);
        String endNode = nearestNode(graph, endX, endY);
        List<String> nodePath = shortestNodePath(graph, startNode, endNode, Map.of());
        return buildRouteFromNodePath(graph, startX, startY, endX, endY, nodePath);
    }

    @Override
    public List<ParkPointResponse> buildRouteFromNodePath(ParkRoadGraph graph,
                                                          BigDecimal startX, BigDecimal startY,
                                                          BigDecimal endX, BigDecimal endY,
                                                          List<String> nodePath) {
        List<ParkPointResponse> route = new ArrayList<>();
        // Phase 4：START/END 仅携带 schematic x/y（调用方未提供 GPS），中间节点携带 GPS 坐标，
        // 供下游 pathLength 使用 haversine 计算真实路径长度（米）。
        route.add(point("START", startX, startY, null, null));
        for (String code : nodePath) {
            ParkRoadGraph.NodeView node = graph.node(code);
            if (node != null) {
                route.add(point(code, node.x(), node.y(), node.coordLng(), node.coordLat()));
            }
        }
        route.add(point("END", endX, endY, null, null));
        return route;
    }

    @Override
    public List<String> shortestNodePathWithPenalties(ParkRoadGraph graph,
                                                      BigDecimal startX, BigDecimal startY,
                                                      BigDecimal endX, BigDecimal endY,
                                                      Map<String, Double> edgePenalties) {
        if (graph.isEmpty() || startX == null || startY == null || endX == null || endY == null) {
            return List.of();
        }
        String startNode = nearestNode(graph, startX, startY);
        String endNode = nearestNode(graph, endX, endY);
        return shortestNodePath(graph, startNode, endNode, edgePenalties == null ? Map.of() : edgePenalties);
    }

    @Override
    public boolean isReachable(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {
        if (startX == null || startY == null || endX == null || endY == null) {
            return false;
        }
        ParkRoadGraph graph = resolveGraph(parkId);
        if (graph.isEmpty()) {
            return true;
        }
        try {
            String startNode = nearestNode(graph, startX, startY);
            String endNode = nearestNode(graph, endX, endY);
            shortestNodePath(graph, startNode, endNode, Map.of());
            return true;
        } catch (BusinessException ex) {
            if ("PARK_ROUTE_NOT_FOUND".equals(ex.getCode())) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * DB has nodes for this park → use DB only; otherwise YAML fallback (P2-10).
     */
    @Override
    public ParkRoadGraph loadGraph(Long parkId) {
        return resolveGraph(parkId);
    }

    ParkRoadGraph resolveGraph(Long parkId) {
        Long resolvedParkId = parkId != null ? parkId : parkStationService.requireDefaultPark().getId();
        // Phase 4：查询时过滤 status=ACTIVE，避免加载 DISABLED 节点/路段
        List<RoadNodeEntity> dbNodes = roadNodeMapper.selectList(new QueryWrapper<RoadNodeEntity>()
                .eq("park_id", resolvedParkId)
                .eq("status", "ACTIVE")
                .eq("deleted", 0));
        if (!dbNodes.isEmpty()) {
            List<RoadSegmentEntity> dbSegments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                    .eq("park_id", resolvedParkId)
                    .eq("status", "ACTIVE")
                    .eq("deleted", 0));
            return ParkRoadGraph.fromDatabase(dbNodes, dbSegments);
        }
        return ParkRoadGraph.fromYaml(parkPilotProperties);
    }

    /**
     * 路径规划入口（ALG-A*）：度量一致且开关开启时走 A*，否则回退 Dijkstra。
     * 未找到路径时抛 {@code PARK_ROUTE_NOT_FOUND}，与既有调用方契约一致。
     */
    private List<String> shortestNodePath(ParkRoadGraph graph, String startNode, String endNode,
                                          Map<String, Double> edgePenalties) {
        RoutePlan plan = planRoute(graph, startNode, endNode, edgePenalties);
        if (!plan.found()) {
            throw new BusinessException("PARK_ROUTE_NOT_FOUND", "Park route not found");
        }
        return plan.nodePath();
    }

    /**
     * 执行一次路径规划，并记录所用算法、总代价与展开节点数（ALG-A*）。
     *
     * <p>算法选择：{@code fsd.park.route-plan.a-star-enabled} 为 true 且图内节点度量一致时用 A*，
     * 否则用 Dijkstra。度量一致指"全部节点带 GPS"或"全部节点仅有 schematic 坐标"——混用会让
     * 边权在米与像素之间跳变，启发函数不再可采纳，此时即使开关为 true 也自动回退。
     *
     * @return 未找到路径时 {@code nodePath} 为空列表、{@code cost} 为 +∞
     */
    RoutePlan planRoute(ParkRoadGraph graph, String startNode, String endNode,
                        Map<String, Double> edgePenalties) {
        Map<String, Double> penalties = edgePenalties == null ? Map.of() : edgePenalties;
        if (Objects.equals(startNode, endNode)) {
            return new RoutePlan(List.of(startNode), 0D, 1, ALGORITHM_TRIVIAL);
        }
        boolean metricConsistent = isMetricConsistent(graph);
        boolean useAStar = parkPilotProperties.getRoutePlan().isAStarEnabled() && metricConsistent;
        double weight = effectiveAStarWeight();
        RoutePlan plan = useAStar
                ? planAStar(graph, startNode, endNode, penalties, weight)
                : planDijkstra(graph, startNode, endNode, penalties);
        if (log.isDebugEnabled()) {
            log.debug("park route planned: algorithm={}, metricConsistent={}, weight={}, expandedNodes={}, cost={}, hops={}",
                    plan.algorithm(), metricConsistent, plan.weight(), plan.expandedNodes(),
                    plan.cost(), plan.nodePath().size());
        }
        return plan;
    }

    /**
     * 生效的加权 A* 权重（T-05）：读取 {@code fsd.park.route-plan.a-star-weight} 并夹取到 ≥ 1.0。
     * 配置缺失或非法（NaN/Infinity/&lt;1）时回退标准 A*（w = 1.0）。
     */
    double effectiveAStarWeight() {
        double configured = parkPilotProperties.getRoutePlan().getAStarWeight();
        if (!Double.isFinite(configured) || configured < 1.0D) {
            return 1.0D;
        }
        return configured;
    }

    /** 标准 A*（w = 1，保证最优）：等价于 {@code planAStar(graph, start, end, penalties, 1.0)}。 */
    RoutePlan planAStar(ParkRoadGraph graph, String startNode, String endNode,
                        Map<String, Double> edgePenalties) {
        return planAStar(graph, startNode, endNode, edgePenalties, 1.0D);
    }

    /**
     * A* / 加权 A* 搜索：f(n) = g(n) + w · h(n)，h(n) 为当前节点到终点的直线距离。
     *
     * <p><b>可采纳性（w = 1）</b>：边权 = 节点间距离 × 系数，系数恒 ≥ 1（trafficMultiplier ≥ 1、
     * MAPF 冲突惩罚 (1 + penalty) ≥ 1），且直线距离满足三角不等式，故 h(n) 不会高估剩余代价。
     * h 同时满足一致性（h(n) ≤ c(n,n') + h(n')），因此每个节点只需展开一次即可取得最优路径。
     *
     * <p><b>加权 A*（w &gt; 1，T-05）</b>：h 被放大 w 倍后不再可采纳，解质量以 {@code w × 最优代价}
     * 为上界，换取更少的展开节点。此时 h 也随之不再一致，故对已展开节点开放"重开"
     * （发现更短 g 时重新入队并移出 expanded 集合）——否则闭集会挡住更优路径，
     * 实际代价将超出 w 倍上界。
     *
     * @param weight 启发权重；非法值（NaN/Infinity/&lt;1）按 1.0 处理
     */
    RoutePlan planAStar(ParkRoadGraph graph, String startNode, String endNode,
                        Map<String, Double> edgePenalties, double weight) {
        double w = Double.isFinite(weight) && weight >= 1.0D ? weight : 1.0D;
        String algorithm = w > 1.0D ? ALGORITHM_WEIGHTED_ASTAR : ALGORITHM_ASTAR;
        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> expanded = new HashSet<>();
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::fScore));
        int expandedCount = 0;
        gScore.put(startNode, 0D);
        open.add(new AStarNode(startNode, 0D, w * heuristic(graph, startNode, endNode)));

        while (!open.isEmpty()) {
            AStarNode current = open.poll();
            if (!expanded.add(current.code())) {
                continue;
            }
            expandedCount++;
            if (Objects.equals(current.code(), endNode)) {
                return new RoutePlan(reconstruct(previous, startNode, endNode),
                        current.gScore(), expandedCount, algorithm, w);
            }
            for (String next : graph.neighbors(current.code())) {
                double step = stepCost(graph, current.code(), next, edgePenalties);
                if (!isUsableStep(step)) {
                    continue;
                }
                double tentative = current.gScore() + step;
                if (tentative < gScore.getOrDefault(next, Double.MAX_VALUE)) {
                    gScore.put(next, tentative);
                    previous.put(next, current.code());
                    // 加权 A* 下 h 不一致，允许重开已展开节点，避免闭集钳制导致代价超出 w 倍上界
                    expanded.remove(next);
                    open.add(new AStarNode(next, tentative, tentative + w * heuristic(graph, next, endNode)));
                }
            }
        }
        return new RoutePlan(List.of(), Double.POSITIVE_INFINITY, expandedCount, algorithm, w);
    }

    /** Dijkstra 全量扩展（回退路径与 A* 的对照基线）。 */
    RoutePlan planDijkstra(ParkRoadGraph graph, String startNode, String endNode,
                           Map<String, Double> edgePenalties) {
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> expanded = new HashSet<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        int expandedCount = 0;
        distances.put(startNode, 0D);
        queue.add(new NodeDistance(startNode, 0D));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (!expanded.add(current.code())) {
                continue;
            }
            expandedCount++;
            if (Objects.equals(current.code(), endNode)) {
                break;
            }
            for (String next : graph.neighbors(current.code())) {
                double step = stepCost(graph, current.code(), next, edgePenalties);
                if (!isUsableStep(step)) {
                    continue;
                }
                double candidate = current.distance() + step;
                if (candidate < distances.getOrDefault(next, Double.MAX_VALUE)) {
                    distances.put(next, candidate);
                    previous.put(next, current.code());
                    queue.add(new NodeDistance(next, candidate));
                }
            }
        }
        if (!previous.containsKey(endNode)) {
            return new RoutePlan(List.of(), Double.POSITIVE_INFINITY, expandedCount, ALGORITHM_DIJKSTRA);
        }
        return new RoutePlan(reconstruct(previous, startNode, endNode),
                distances.getOrDefault(endNode, Double.POSITIVE_INFINITY), expandedCount, ALGORITHM_DIJKSTRA);
    }

    /**
     * 启发函数 h(n)：节点到终点的直线距离，使用与 {@link ParkRoadGraph#edgeCost} 相同的度量
     * （两端均有 GPS 时为 Haversine 米，否则为 schematic 像素）。
     */
    static double heuristic(ParkRoadGraph graph, String from, String to) {
        ParkRoadGraph.NodeView fromNode = graph.node(from);
        ParkRoadGraph.NodeView toNode = graph.node(to);
        if (fromNode == null || toNode == null) {
            return 0D;
        }
        return fromNode.distanceTo(toNode);
    }

    /**
     * 图内度量是否一致（ALG-A*）：全部节点带 GPS，或全部节点仅有 schematic 坐标。
     * 混用会让边权在"米"与"像素"间跳变，启发函数不再可采纳，此时必须回退 Dijkstra。
     */
    static boolean isMetricConsistent(ParkRoadGraph graph) {
        boolean gpsSeen = false;
        boolean pixelSeen = false;
        for (ParkRoadGraph.NodeView node : graph.nodes().values()) {
            boolean hasGps = node.coordLng() != null && node.coordLat() != null;
            gpsSeen |= hasGps;
            pixelSeen |= !hasGps;
            if (gpsSeen && pixelSeen) {
                return false;
            }
        }
        return true;
    }

    /** 单步边代价：路网距离 × (1 + MAPF 冲突惩罚)，A* 与 Dijkstra 使用同一公式。 */
    private static double stepCost(ParkRoadGraph graph, String from, String to, Map<String, Double> edgePenalties) {
        double base = graph.edgeCost(from, to);
        if (base >= Double.MAX_VALUE / 2) {
            return Double.MAX_VALUE;
        }
        return base * (1D + edgePenalties.getOrDefault(directedKey(from, to), 0D));
    }

    private static boolean isUsableStep(double step) {
        return Double.isFinite(step) && step < Double.MAX_VALUE / 2;
    }

    /** 由 previous 指针回溯路径；起点未连通时返回空列表。 */
    private static List<String> reconstruct(Map<String, String> previous, String startNode, String endNode) {
        List<String> path = new ArrayList<>();
        String cursor = endNode;
        path.add(cursor);
        int guard = previous.size() + 2;
        while (previous.containsKey(cursor) && guard-- > 0) {
            cursor = previous.get(cursor);
            path.add(0, cursor);
        }
        if (!Objects.equals(path.get(0), startNode)) {
            return List.of();
        }
        return path;
    }

    private static String directedKey(String from, String to) {
        return from + ">" + to;
    }

    private String nearestNode(ParkRoadGraph graph, BigDecimal x, BigDecimal y) {
        return graph.nodes().values().stream()
                .min(Comparator.comparingDouble(node -> node.distanceTo(x, y)))
                .map(ParkRoadGraph.NodeView::code)
                .orElseThrow(() -> new BusinessException("PARK_ROUTE_NODE_NOT_FOUND", "Park route node not found"));
    }

    private ParkPointResponse point(String code, BigDecimal x, BigDecimal y, BigDecimal lng, BigDecimal lat) {
        return ParkPointResponse.builder()
                .code(code)
                .x(x)
                .y(y)
                .longitude(lng)
                .latitude(lat)
                .build();
    }

    private record NodeDistance(String code, double distance) {
    }

    private record AStarNode(String code, double gScore, double fScore) {
    }

    /**
     * 一次路径规划的结果（ALG-A*）。
     *
     * @param nodePath      节点序列；未找到路径时为空列表
     * @param cost          最优总代价（米或像素，取决于图内度量）；未找到路径时为 +∞
     * @param expandedNodes 展开（出队并松弛）的节点数，用于对比 A* 与 Dijkstra 的搜索规模
     * @param algorithm     {@link #ALGORITHM_ASTAR} / {@link #ALGORITHM_WEIGHTED_ASTAR} /
     *                      {@link #ALGORITHM_DIJKSTRA} / {@link #ALGORITHM_TRIVIAL}
     * @param weight        生效的启发权重 w（T-05）；Dijkstra 与 TRIVIAL 恒为 1.0
     */
    record RoutePlan(List<String> nodePath, double cost, int expandedNodes, String algorithm, double weight) {

        /** 标准 A* / Dijkstra / TRIVIAL 结果（w = 1.0，保证最优）。 */
        RoutePlan(List<String> nodePath, double cost, int expandedNodes, String algorithm) {
            this(nodePath, cost, expandedNodes, algorithm, 1.0D);
        }

        boolean found() {
            return !nodePath.isEmpty();
        }
    }
}
