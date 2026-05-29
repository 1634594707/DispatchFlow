package com.fsd.dispatch.road;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory road graph for a single park (P2-09 / P2-10).
 */
public final class ParkRoadGraph {

    private final Map<String, NodeView> nodes;
    private final Map<String, List<String>> adjacency;

    private ParkRoadGraph(Map<String, NodeView> nodes, Map<String, List<String>> adjacency) {
        this.nodes = nodes;
        this.adjacency = adjacency;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Map<String, NodeView> nodes() {
        return nodes;
    }

    public Map<String, List<String>> adjacency() {
        return adjacency;
    }

    public NodeView node(String code) {
        return nodes.get(code);
    }

    public List<String> neighbors(String code) {
        return adjacency.getOrDefault(code, List.of());
    }

    public static ParkRoadGraph fromDatabase(List<RoadNodeEntity> dbNodes, List<RoadSegmentEntity> dbSegments) {
        Map<String, NodeView> nodes = new HashMap<>();
        for (RoadNodeEntity entity : dbNodes) {
            if (entity == null || entity.getNodeCode() == null) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
                continue;
            }
            nodes.put(entity.getNodeCode(), new NodeView(entity.getNodeCode(), entity.getCoordX(), entity.getCoordY()));
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String code : nodes.keySet()) {
            adjacency.put(code, new ArrayList<>());
        }
        for (RoadSegmentEntity segment : dbSegments) {
            if (segment == null || !"ACTIVE".equalsIgnoreCase(segment.getStatus())) {
                continue;
            }
            link(adjacency, segment.getFromNodeCode(), segment.getToNodeCode());
            link(adjacency, segment.getToNodeCode(), segment.getFromNodeCode());
        }
        return new ParkRoadGraph(nodes, adjacency);
    }

    public static ParkRoadGraph fromYaml(ParkPilotProperties properties) {
        Map<String, NodeView> nodes = new HashMap<>();
        for (ParkPilotProperties.RoadNodeConfig config : properties.getRoadNodes()) {
            nodes.put(config.getCode(), new NodeView(config.getCode(), config.getX(), config.getY()));
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String code : nodes.keySet()) {
            adjacency.put(code, new ArrayList<>());
        }
        for (ParkPilotProperties.RoadSegmentConfig segment : properties.getRoadSegments()) {
            link(adjacency, segment.getFrom(), segment.getTo());
            link(adjacency, segment.getTo(), segment.getFrom());
        }
        return new ParkRoadGraph(nodes, adjacency);
    }

    private static void link(Map<String, List<String>> adjacency, String from, String to) {
        if (from == null || to == null || !adjacency.containsKey(from) || !adjacency.containsKey(to)) {
            return;
        }
        List<String> neighbors = adjacency.get(from);
        if (!neighbors.contains(to)) {
            neighbors.add(to);
        }
    }

    public record NodeView(String code, BigDecimal x, BigDecimal y) {

        public double distanceTo(BigDecimal otherX, BigDecimal otherY) {
            double dx = x.doubleValue() - otherX.doubleValue();
            double dy = y.doubleValue() - otherY.doubleValue();
            return Math.hypot(dx, dy);
        }

        public double distanceTo(NodeView other) {
            return distanceTo(other.x, other.y);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NodeView other)) {
                return false;
            }
            return Objects.equals(code, other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(code);
        }
    }
}
