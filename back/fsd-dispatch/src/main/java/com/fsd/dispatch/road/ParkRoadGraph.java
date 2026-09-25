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

    /**
     * 示意坐标 px → 米的换算系数，<b>两个轴不一样</b>。<b>仅当节点无 GPS 时</b>作兜底：
     * 扩范围后 ACTIVE 节点 91/91 带 GPS，距离一律走 haversine 米，这条路在生产不触发，
     * 故它与 §13.30 的 fit_canvas 画布**无关**（画布改的是 coord_x/y 与前端/后端 affine，不是这个像素兜底）。
     *
     * <p>由 V38 的线性映射反解：{@code x=(lng-121.072)*77000} 且本纬度 1° 经度 ≈ 94 430 m
     * ⇒ 1.2263 m/px；{@code y=(31.9645-lat)*150000} 且 1° 纬度 ≈ 110 852 m ⇒ 0.7390 m/px。
     * 对 seed 里 86 条 ACTIVE↔ACTIVE 边实测的 米/hypot(px) 比值：min 0.741、均值 0.999、max 1.226。
     */
    public static final double METRES_PER_PX_X = 1.2263D;
    public static final double METRES_PER_PX_Y = 0.7390D;

    private static final com.fasterxml.jackson.databind.ObjectMapper POLYLINE_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final Map<String, NodeView> nodes;
    private final Map<String, List<String>> adjacency;
    private final Map<String, Double> edgeCostMultiplier;
    /**
     * 每条有向边的 GCJ-02 形状点链（含两端节点），键 {@code from>to}。
     *
     * <p>为什么必须带：A* 只给节点序列，而 633 条 ACTIVE 边里 275 条是 700–2,740 m 的"两个路口之间一条边"。
     * 只输出节点 = 在那些长边上拉直线，这正是"路线不沿实路"的成因。折线本身库里一直有
     * （{@code t_road_segment.polyline_geojson}），只是从没进过图对象。
     *
     * <p>只可能有值的路径是 {@link #fromDatabase}；YAML 图没有边几何，那里拿到的是空表。
     */
    private final Map<String, List<double[]>> edgeGeometries;
    /** 懒算缓存，见 {@link #reverseAdjacency()}；除首次构造外只读。 */
    private volatile Map<String, List<String>> reverseAdjacency;

    private ParkRoadGraph(Map<String, NodeView> nodes,
                          Map<String, List<String>> adjacency,
                          Map<String, Double> edgeCostMultiplier) {
        this(nodes, adjacency, edgeCostMultiplier, Map.of());
    }

    private ParkRoadGraph(Map<String, NodeView> nodes,
                          Map<String, List<String>> adjacency,
                          Map<String, Double> edgeCostMultiplier,
                          Map<String, List<double[]>> edgeGeometries) {
        this.nodes = nodes;
        this.adjacency = adjacency;
        this.edgeCostMultiplier = edgeCostMultiplier;
        this.edgeGeometries = edgeGeometries;
    }

    public static String edgeKey(String from, String to) {
        return from + ">" + to;
    }

    /** 该有向边的 GCJ-02 形状点链；无几何（未 seed 或被过滤掉）时返回空表。 */
    public List<double[]> edgeGeometry(String from, String to) {
        return edgeGeometries.getOrDefault(edgeKey(from, to), List.of());
    }

    public int edgeGeometryCount() {
        return edgeGeometries.size();
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

    /**
     * 反向邻接表：从"目标点"往回走能到哪些节点，一次 BFS 就等价于对每台候选车跑一次可达性判定。
     * 图是不可变的（{@code fromDatabase} 构造后不再改），所以懒算一次并缓存。
     * 方向语义直接沿用 {@link #adjacency()} —— BIDIRECTIONAL 已被展开成两条，FORWARD 只有一条。
     */
    public Map<String, List<String>> reverseAdjacency() {
        Map<String, List<String>> cached = reverseAdjacency;
        if (cached == null) {
            Map<String, List<String>> reverse = new HashMap<>();
            adjacency.forEach((from, tos) -> tos.forEach(to ->
                    reverse.computeIfAbsent(to, k -> new ArrayList<>()).add(from)));
            reverse.replaceAll((to, froms) -> List.copyOf(froms));
            cached = Map.copyOf(reverse);
            reverseAdjacency = cached;
        }
        return cached;
    }

    /**
     * 离给定位置最近的节点。刻意与 {@code ParkRoutePlannerServiceImpl#nearestNode} 用同一把尺
     * （示意 px 欧氏、同一个 {@link NodeView#distanceTo}）—— 预筛和寻路只要口径不同，
     * 就会把"其实送得到"的车剪掉，那是最难查的一类错。
     */
    public String nearestNodeCode(BigDecimal x, BigDecimal y) {
        return nodes.values().stream()
                .min(java.util.Comparator.comparingDouble(node -> node.distanceTo(x, y)))
                .map(NodeView::code)
                .orElse(null);
    }

    public double edgeCost(String from, String to) {
        NodeView fromNode = node(from);
        NodeView toNode = node(to);
        if (fromNode == null || toNode == null) {
            return Double.MAX_VALUE;
        }
        // Phase 4：edgeCost 返回真实距离（米）。有 GPS 用 haversine；没有则按 V38 的两轴系数把
        // 示意 px 换算成米（见 METRES_PER_PX_X/Y），所以单位始终是米，不再是"有/没有 GPS 两种单位"。
        double base = fromNode.distanceMetersTo(toNode);
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
        Map<String, List<double[]>> edgeGeometries = new HashMap<>();
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
            // 边几何：正向按 seed 里存的顺序，反向把同一条折线倒过来，拼接时才不会跳回起点
            List<double[]> geometry = parsePolylineGeojson(segment.getPolylineGeojson());
            if (!geometry.isEmpty()) {
                if (forward) {
                    edgeGeometries.put(edgeKey(segment.getFromNodeCode(), segment.getToNodeCode()), geometry);
                }
                if (reverse) {
                    List<double[]> backwards = new ArrayList<>(geometry);
                    java.util.Collections.reverse(backwards);
                    edgeGeometries.put(edgeKey(segment.getToNodeCode(), segment.getFromNodeCode()), backwards);
                }
            }
        }
        return new ParkRoadGraph(nodes, adjacency, edgeCostMultiplier, Map.copyOf(edgeGeometries));
    }

    /**
     * 解析 {@code t_road_segment.polyline_geojson}。库里存的是<b>裸</b>坐标数组
     * {@code [[lng,lat],...]}（{@code scripts/geo/osm_to_road_graph.py} 的 emit_sql 就是这么写的），
     * 不是带 {@code "type":"LineString"} 的完整 GeoJSON 对象 —— 两种都吃，因为
     * {@code zjf_amap_terminal_links.sql} 那批边将来补几何时会按标准 GeoJSON 灌。
     *
     * <p>坐标系是 GCJ-02。解不出来就返回空表，让调用方退回"只有节点"的折线 ——
     * 宁可得一条粗线，也不要因为一行脏数据就把整张图建不起来。
     */
    private static List<double[]> parsePolylineGeojson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = POLYLINE_MAPPER.readTree(raw);
            com.fasterxml.jackson.databind.JsonNode coords = root.isArray() ? root : root.path("coordinates");
            if (!coords.isArray() || coords.size() < 2) {
                return List.of();
            }
            List<double[]> points = new ArrayList<>(coords.size());
            for (com.fasterxml.jackson.databind.JsonNode pair : coords) {
                if (!pair.isArray() || pair.size() < 2 || !pair.get(0).isNumber() || !pair.get(1).isNumber()) {
                    return List.of();
                }
                points.add(new double[]{pair.get(0).asDouble(), pair.get(1).asDouble()});
            }
            return List.copyOf(points);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
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
         *
         * <p><b>返回值单位取决于数据</b>：有 GPS 是米，没有就是示意 px，两者在本 seed 上差
         * 0.74–1.23 倍。需要米的地方（任何"按距离折算时间"）请改用 {@link #distanceMetersTo}；
         * 只有"相对比较"（如找最近节点）才可以直接用本方法。
         */
        public double distanceTo(NodeView other) {
            if (coordLng != null && coordLat != null && other.coordLng != null && other.coordLat != null) {
                return haversineMeters(coordLng, coordLat, other.coordLng, other.coordLat);
            }
            return distanceTo(other.x, other.y);
        }

        /**
         * 与 {@link #distanceTo(NodeView)} 同源，但**返回值恒为米**。
         *
         * <p>为什么要单独一个方法：schematic 坐标不是等距方格。V38 的线性映射是
         * {@code x=(lng-121.072)*77000}、{@code y=(31.9645-lat)*150000}，而本纬度上
         * 1° 经度 ≈ 94 430 m、1° 纬度 ≈ 110 852 m ⇒ <b>1 px 横向 ≈ 1.226 m、纵向 ≈ 0.741 m</b>。
         * 拿 px 当米用，误差按航向在 0.74×–1.23× 之间摆（对 seed 里 86 条 ACTIVE↔ACTIVE 边实测：
         * 比值 min 0.741 / 均值 0.999 / max 1.226，标准差 0.231）。均值恰好接近 1，
         * 所以这个错平时看不出来 —— 但逐条边能差 ±23%，任何"按距离折算时间"的地方都会错。
         *
         * <p>现役数据里所有 ACTIVE 节点都有 GPS（实测 55/55），所以本方法的 px 分支今天走不到；
         * 它存在的意义是**以后走到时也不会算错**（例如新图节点还没回填经纬度）。
         */
        public double distanceMetersTo(NodeView other) {
            if (coordLng != null && coordLat != null && other.coordLng != null && other.coordLat != null) {
                return haversineMeters(coordLng, coordLat, other.coordLng, other.coordLat);
            }
            double dx = (x.doubleValue() - other.x.doubleValue()) * METRES_PER_PX_X;
            double dy = (y.doubleValue() - other.y.doubleValue()) * METRES_PER_PX_Y;
            return Math.hypot(dx, dy);
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
