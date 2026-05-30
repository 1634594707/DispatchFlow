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
import org.springframework.stereotype.Service;

@Service
public class ParkRoutePlannerServiceImpl implements ParkRoutePlannerService {

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
            return List.of(
                    point("START", startX, startY),
                    point("END", endX, endY)
            );
        }

        String startNode = nearestNode(graph, startX, startY);
        String endNode = nearestNode(graph, endX, endY);
        List<String> nodePath = shortestNodePath(graph, startNode, endNode);

        List<ParkPointResponse> route = new ArrayList<>();
        route.add(point("START", startX, startY));
        for (String code : nodePath) {
            ParkRoadGraph.NodeView node = graph.node(code);
            route.add(point(code, node.x(), node.y()));
        }
        route.add(point("END", endX, endY));
        return route;
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
            shortestNodePath(graph, startNode, endNode);
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
    ParkRoadGraph resolveGraph(Long parkId) {
        Long resolvedParkId = parkId != null ? parkId : parkStationService.requireDefaultPark().getId();
        List<RoadNodeEntity> dbNodes = roadNodeMapper.selectList(new QueryWrapper<RoadNodeEntity>()
                .eq("park_id", resolvedParkId)
                .eq("deleted", 0));
        if (!dbNodes.isEmpty()) {
            List<RoadSegmentEntity> dbSegments = roadSegmentMapper.selectList(new QueryWrapper<RoadSegmentEntity>()
                    .eq("park_id", resolvedParkId)
                    .eq("deleted", 0));
            return ParkRoadGraph.fromDatabase(dbNodes, dbSegments);
        }
        return ParkRoadGraph.fromYaml(parkPilotProperties);
    }

    private List<String> shortestNodePath(ParkRoadGraph graph, String startNode, String endNode) {
        if (Objects.equals(startNode, endNode)) {
            return List.of(startNode);
        }
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        distances.put(startNode, 0D);
        queue.add(new NodeDistance(startNode, 0D));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (!visited.add(current.code())) {
                continue;
            }
            if (Objects.equals(current.code(), endNode)) {
                break;
            }
            for (String next : graph.neighbors(current.code())) {
                double candidate = current.distance() + graph.edgeCost(current.code(), next);
                if (candidate < distances.getOrDefault(next, Double.MAX_VALUE)) {
                    distances.put(next, candidate);
                    previous.put(next, current.code());
                    queue.add(new NodeDistance(next, candidate));
                }
            }
        }

        if (!previous.containsKey(endNode) && !Objects.equals(startNode, endNode)) {
            throw new BusinessException("PARK_ROUTE_NOT_FOUND", "Park route not found");
        }

        List<String> path = new ArrayList<>();
        String cursor = endNode;
        path.add(cursor);
        while (previous.containsKey(cursor)) {
            cursor = previous.get(cursor);
            path.add(0, cursor);
        }
        return path;
    }

    private String nearestNode(ParkRoadGraph graph, BigDecimal x, BigDecimal y) {
        return graph.nodes().values().stream()
                .min(Comparator.comparingDouble(node -> node.distanceTo(x, y)))
                .map(ParkRoadGraph.NodeView::code)
                .orElseThrow(() -> new BusinessException("PARK_ROUTE_NODE_NOT_FOUND", "Park route node not found"));
    }

    private ParkPointResponse point(String code, BigDecimal x, BigDecimal y) {
        return ParkPointResponse.builder()
                .code(code)
                .x(x)
                .y(y)
                .build();
    }

    private record NodeDistance(String code, double distance) {
    }
}
