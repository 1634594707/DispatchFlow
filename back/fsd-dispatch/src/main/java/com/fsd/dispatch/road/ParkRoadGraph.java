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
    private final Map<String, Double> edgeCostMultiplier;

    private ParkRoadGraph(Map<String, NodeView> nodes,
                          Map<String, List<String>> adjacency,
                          Map<String, Double> edgeCostMultiplier) {
        this.nodes = nodes;
        this.adjacency = adjacency;
        this.edgeCostMultiplier = edgeCostMultiplier;
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

    public double edgeCost(String from, String to) {
        NodeView fromNode = node(from);
        NodeView toNode = node(to);
        if (fromNode == null || toNode == null) {
            return Double.MAX_VALUE;
        }
        // Phase 4：edgeCost 返回真实距离（米）。当节点携带 coordLng/coordLat 时用 haversine，
        // 否则回退到 schematic 欧几里得距离（仅用于相对路径选择，单位为像素）。
        double base = fromNode.distanceTo(toNode);
        return base * edgeCostMultiplier.getOrDefault(directedKey(from, to), 1.0);
    }

    public static ParkRoadGraph fromDatabase(List<RoadNodeEntity> dbNodes, List<RoadSegmentEntity> dbSegments) {
        return fromDatabase(dbNodes, dbSegments, java.time.LocalDateTime.now());
    }

    /**
     * Builds the road graph with access-state and time-window filtering (P1-2).
     *
     * Filtering rules:
     *   - status != ACTIVE → skip (existing behavior)
     *   - access_state = BLOCKED or PEDESTRIAN_ONLY → skip (no vehicle may use)
     *   - current time falls within [blocked_from, blocked_until) → skip (临时封路)
     *
     * Vehicle-specific filtering (vehicle type, width, road class) is applied
     * separately via {@link #buildForVehicle} at trip-planning time, since the
     * graph itself is per-park, not per-vehicle.
     */
    public static ParkRoadGraph fromDatabase(List<RoadNodeEntity> dbNodes, List<RoadSegmentEntity> dbSegments,
                                              java.time.LocalDateTime now) {
        Map<String, NodeView> nodes = new HashMap<>();
        for (RoadNodeEntity entity : dbNodes) {
            if (entity == null || entity.getNodeCode() == null) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
                continue;
            }
            // Phase 4：携带 coordLng/coordLat 以便 edgeCost 使用 haversine 计算真实距离
            nodes.put(entity.getNodeCode(), new NodeView(entity.getNodeCode(), entity.getCoordX(),
                    entity.getCoordY(), entity.getCoordLng(), entity.getCoordLat()));
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String code : nodes.keySet()) {
            adjacency.put(code, new ArrayList<>());
        }
        Map<String, Double> edgeCostMultiplier = new HashMap<>();
        for (RoadSegmentEntity segment : dbSegments) {
            if (segment == null || !"ACTIVE".equalsIgnoreCase(segment.getStatus())) {
                continue;
            }
            // P1-2：通行语义过滤 — BLOCKED / PEDESTRIAN_ONLY 路段对所有车辆禁行
            String accessState = segment.getAccessState();
            if (accessState != null && !accessState.isBlank()) {
                String norm = accessState.trim().toUpperCase(java.util.Locale.ROOT);
                if ("BLOCKED".equals(norm) || "PEDESTRIAN_ONLY".equals(norm)) {
                    continue;
                }
            }
            // P1-2：临时封路时间窗过滤
            if (isCurrentlyBlocked(segment, now)) {
                continue;
            }
            // 阶段七 7.1：根据 direction 字段决定建边方向。
            //   BIDIRECTIONAL（默认/空）= 双向；FORWARD = 仅 from→to；REVERSE = 仅 to→from。
            String dir = segment.getDirection();
            boolean forward = true;
            boolean reverse = true;
            if (dir != null && !dir.isBlank()) {
                String norm = dir.trim().toUpperCase(java.util.Locale.ROOT);
                switch (norm) {
                    case "FORWARD" -> reverse = false;
                    case "REVERSE" -> forward = false;
                    // BIDIRECTIONAL 或未知值：双向（向后兼容）
                    default -> { /* keep both true */ }
                }
            }
            if (forward) {
                link(adjacency, segment.getFromNodeCode(), segment.getToNodeCode());
            }
            if (reverse) {
                link(adjacency, segment.getToNodeCode(), segment.getFromNodeCode());
            }
            double multiplier = trafficMultiplier(segment);
            if (forward) {
                putMultiplier(edgeCostMultiplier, segment.getFromNodeCode(), segment.getToNodeCode(), multiplier);
            }
            if (reverse) {
                putMultiplier(edgeCostMultiplier, segment.getToNodeCode(), segment.getFromNodeCode(), multiplier);
            }
        }
        return new ParkRoadGraph(nodes, adjacency, edgeCostMultiplier);
    }

    /**
     * Whether the segment is currently within its temporary block window (P1-2).
     * A segment is blocked when both blockedFrom and blockedUntil are non-null and
     * `now` falls in [blockedFrom, blockedUntil). A segment with only blockedFrom set
     * (no end) is treated as permanently blocked.
     */
    private static boolean isCurrentlyBlocked(RoadSegmentEntity segment, java.time.LocalDateTime now) {
        java.time.LocalDateTime from = segment.getBlockedFrom();
        if (from == null) {
            return false;
        }
        if (now.isBefore(from)) {
            return false;
        }
        java.time.LocalDateTime until = segment.getBlockedUntil();
        if (until == null) {
            return true; // 永久封路
        }
        return now.isBefore(until);
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
        return new ParkRoadGraph(nodes, adjacency, Map.of());
    }

    private static double trafficMultiplier(RoadSegmentEntity segment) {
        double multiplier = 1.0;
        int congestion = segment.getCongestionLevel() == null ? 0 : segment.getCongestionLevel();
        if (congestion > 0) {
            multiplier += congestion * 0.35;
        }
        Integer speedLimit = segment.getSpeedLimitKmh();
        if (speedLimit != null && speedLimit < 10) {
            multiplier += 0.5;
        }
        return multiplier;
    }

    private static void putMultiplier(Map<String, Double> multipliers, String from, String to, double value) {
        if (from == null || to == null) {
            return;
        }
        multipliers.put(directedKey(from, to), value);
    }

    private static String directedKey(String from, String to) {
        return from + ">" + to;
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

    public record NodeView(String code, BigDecimal x, BigDecimal y, BigDecimal coordLng, BigDecimal coordLat) {

        /** Backward-compatible constructor for YAML/schematic-only nodes (no GPS). */
        public NodeView(String code, BigDecimal x, BigDecimal y) {
            this(code, x, y, null, null);
        }

        /**
         * Schematic Euclidean distance on coord_x/coord_y. Used for nearest-node
         * finding where only relative comparison matters.
         */
        public double distanceTo(BigDecimal otherX, BigDecimal otherY) {
            double dx = x.doubleValue() - otherX.doubleValue();
            double dy = y.doubleValue() - otherY.doubleValue();
            return Math.hypot(dx, dy);
        }

        /**
         * Phase 4：节点间距离。当两端均携带 GPS 坐标时返回 haversine 米，否则回退到 schematic 欧几里得。
         * {@link ParkRoadGraph#edgeCost} 使用此方法，故路径代价单位为米（GPS 可用时）。
         */
        public double distanceTo(NodeView other) {
            if (coordLng != null && coordLat != null && other.coordLng != null && other.coordLat != null) {
                return haversineMeters(coordLng, coordLat, other.coordLng, other.coordLat);
            }
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

    /**
     * Phase 4：haversine 大圆距离（米）。与 {@code GeoPolygonUtils.haversineMeters} 算法一致，
     * 此处独立实现以避免 road 包对 geo 包的耦合依赖。
     */
    static double haversineMeters(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        double lat1r = Math.toRadians(lat1.doubleValue());
        double lat2r = Math.toRadians(lat2.doubleValue());
        double dLat = lat2r - lat1r;
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1r) * Math.cos(lat2r) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000D * Math.asin(Math.sqrt(h));
    }
}
