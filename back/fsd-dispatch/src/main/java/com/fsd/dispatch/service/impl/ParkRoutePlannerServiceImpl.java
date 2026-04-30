package com.fsd.dispatch.service.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.service.ParkRoutePlannerService;
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

    public ParkRoutePlannerServiceImpl(ParkPilotProperties parkPilotProperties) {
        this.parkPilotProperties = parkPilotProperties;
    }

    @Override
    public List<ParkPointResponse> buildRoute(BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {
        if (startX == null || startY == null || endX == null || endY == null) {
            return List.of();
        }
        if (parkPilotProperties.getRoadNodes().isEmpty() || parkPilotProperties.getRoadSegments().isEmpty()) {
            return List.of(
                    point("START", startX, startY),
                    point("END", endX, endY)
            );
        }

        String startNode = nearestNode(startX, startY);
        String endNode = nearestNode(endX, endY);
        List<String> nodePath = shortestNodePath(startNode, endNode);

        List<ParkPointResponse> route = new ArrayList<>();
        route.add(point("START", startX, startY));
        for (String code : nodePath) {
            ParkPilotProperties.RoadNodeConfig node = getNode(code);
            route.add(point(code, node.getX(), node.getY()));
        }
        route.add(point("END", endX, endY));
        return route;
    }

    private List<String> shortestNodePath(String startNode, String endNode) {
        if (Objects.equals(startNode, endNode)) {
            return List.of(startNode);
        }
        Map<String, List<String>> graph = buildGraph();
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
            for (String next : graph.getOrDefault(current.code(), List.of())) {
                double candidate = current.distance() + distance(getNode(current.code()), getNode(next));
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

    private Map<String, List<String>> buildGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        for (ParkPilotProperties.RoadNodeConfig node : parkPilotProperties.getRoadNodes()) {
            graph.put(node.getCode(), new ArrayList<>());
        }
        for (ParkPilotProperties.RoadSegmentConfig segment : parkPilotProperties.getRoadSegments()) {
            graph.computeIfAbsent(segment.getFrom(), key -> new ArrayList<>()).add(segment.getTo());
            graph.computeIfAbsent(segment.getTo(), key -> new ArrayList<>()).add(segment.getFrom());
        }
        return graph;
    }

    private String nearestNode(BigDecimal x, BigDecimal y) {
        return parkPilotProperties.getRoadNodes().stream()
                .min(Comparator.comparingDouble(node -> distance(x, y, node.getX(), node.getY())))
                .map(ParkPilotProperties.RoadNodeConfig::getCode)
                .orElseThrow(() -> new BusinessException("PARK_ROUTE_NODE_NOT_FOUND", "Park route node not found"));
    }

    private ParkPilotProperties.RoadNodeConfig getNode(String code) {
        return parkPilotProperties.getRoadNodes().stream()
                .filter(node -> Objects.equals(node.getCode(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PARK_ROUTE_NODE_NOT_FOUND", "Park route node not found"));
    }

    private double distance(ParkPilotProperties.RoadNodeConfig from, ParkPilotProperties.RoadNodeConfig to) {
        return distance(from.getX(), from.getY(), to.getX(), to.getY());
    }

    private double distance(BigDecimal fromX, BigDecimal fromY, BigDecimal toX, BigDecimal toY) {
        double dx = fromX.doubleValue() - toX.doubleValue();
        double dy = fromY.doubleValue() - toY.doubleValue();
        return Math.hypot(dx, dy);
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
