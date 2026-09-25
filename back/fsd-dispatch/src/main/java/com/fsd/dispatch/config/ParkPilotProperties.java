package com.fsd.dispatch.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.park")
public class ParkPilotProperties {

    private boolean enabled = true;

    private String defaultParkCode = "DEFAULT";

    private Integer width = 1200;

    private Integer height = 800;

    private Integer minZoom = -1;

    private Integer maxZoom = 3;

    private BigDecimal vehicleSpeedPxPerSecond = new BigDecimal("8");

    private String xFieldAlias = "currentLongitude";

    private String yFieldAlias = "currentLatitude";

    private List<StationConfig> stations = new ArrayList<>();

    private List<PointConfig> parkingSpots = new ArrayList<>();

    private List<RoadNodeConfig> roadNodes = new ArrayList<>();

    private List<RoadSegmentConfig> roadSegments = new ArrayList<>();

    private SimulationConfig simulation = new SimulationConfig();

    private GeoConfig geo = new GeoConfig();

    /** 路网寻路算法配置（A* / Dijkstra 切换，ALG-A*）。 */
    private RoutePlanConfig routePlan = new RoutePlanConfig();

    @Data
    public static class RoutePlanConfig {

        /**
         * A* 开关，默认开启。关闭后回退 Dijkstra 全量扩展（回滚开关）。
         *
         * <p>当图内节点 GPS 覆盖不一致（部分节点带 coordLng/coordLat、部分仅有 schematic
         * 像素坐标）时，边权会在"米"与"像素"之间混用，启发函数不再可采纳，此时无论该开关
         * 取值如何都会自动回退 Dijkstra，以保证最优性。
         */
        private boolean aStarEnabled = true;

        /**
         * 加权 A* 的启发权重 w（T-05）：f(n) = g(n) + w · h(n)。
         *
         * <p>取值语义：
         * <ul>
         *   <li>{@code 1.0}（默认）= 标准 A*，h 可采纳且一致，保证最优解</li>
         *   <li>{@code >1.0} = 加权 A*，牺牲最优性换取搜索规模：解代价上界为 {@code w × 最优代价}，
         *       展开节点数随 w 增大而下降，适用于"路径质量可让步、规划时延敏感"的场景</li>
         * </ul>
         * 取值小于 1.0 会被夹取为 1.0（w&lt;1 只会削弱启发强度，不产生收益）。
         */
        private double aStarWeight = 1.0D;

        /**
         * 路网图 JVM 内缓存存活时间（毫秒）；<=0 表示每次全量重查（修复前行为，仅用于回滚）。
         *
         * <p>选车路径原先每台候选车触发 6 次全量节点+路段查询，是热路径时延的主要成因；
         * 该"P95 277 ms"是 **H2 合成基准**口径（100 车 500 单 1000 节点），本机真 MySQL 实测是
         * P50 179.7 ms / P95 696.5 ms（带 95% CI，见路线图 §13.81）。引这个数必须同时报仪表。
         * 放大到 M2E 的 400-600 节点规模后会退化成秒级。缓存放在进程内而不是 Redis：
         * 邻接表是 MB 量级，走 Redis 反而把时延抬回网络往返。
         *
         * <p>代价是路段临时封路时间窗（access_state / blocked_from）最多滞后一个 TTL，
         * 且围栏/路网变更需等到过期或调用 invalidateGraphCache 才生效。
         */
        private long graphCacheTtlMs = 60_000L;
    }

    @Data
    public static class GeoConfig {

        /** Map park schematic x/y to GCJ-02 around 叠石桥家纺产业带. */
        private boolean enabled = true;

        private BigDecimal anchorLng = new BigDecimal("121.080354");

        private BigDecimal anchorLat = new BigDecimal("31.961977");

        /**
         * 画布米制宽/高。**类型必须是 BigDecimal**：§13.30 画布重定标后真值是 2215.50 × 1891.50 m
         * （与 {@code back/sql/seed/zjf_geo.sql} 的 {@code t_park} 行同源），而 {@code application.yml}
         * 的默认值就写着 {@code 1891.5} —— 曾是 Integer，宿主机不起 env 直接
         * {@code NumberFormatException} 起不来（实测于 §13.54）。
         */
        private BigDecimal parkWidthMeters = new BigDecimal("2215.50");

        private BigDecimal parkHeightMeters = new BigDecimal("1891.50");

        private String scenario = "ZJF_DIESHIQIAO_PILOT";

        /** Path to pilot_osm_geo.json; empty = auto-detect data/pilot_osm_geo.json */
        private String osmGeoPath = "";

        /**
         * Phase 4：4 点仿射变换参考点。至少 3 个非共线点可计算最小二乘仿射变换，
         * 替代单点锚点+均匀缩放，消除 schematic→GPS 转换的旋转/倾斜误差。
         * 验收：围栏内/外判定误差 < 10 米。
         */
        private List<GeoReferencePoint> referencePoints = new ArrayList<>();

        /**
         * 任意点下单的吸附半径（米，按 GPS 大圆距离量，不按示意 px —— px 两轴比例不同，
         * 横向 1.226 m/px、纵向 0.741 m/px，拿 px 当米会按航向摆 ±23%）。
         *
         * <p>250 m 不是拍的：2026-09-23 对现役 417 个可派单节点按 250 m 栅格量"到最近节点"距离，
         * 66.27 km² 提取框里当时只有 <b>32.2 km²（49%）</b>落在这个半径内（150 m 只剩 18.0 km²）。
         * 服务范围按"吸得到"定档、不按图框大小定；W2-c 拆边 + W3-c 重画围栏后的<b>现值是 47.18 km²
         * （1 片 ACTIVE 围栏 `ZJF-ZONE-SVC-01`）</b>，32.2 是那次标定的历史量。
         * 超出半径一律拒单并给原因，
         * 不允许静默兜底成穿墙直线（§0.2 那条死守卫就是这么把不可达伪装成可达的）。
         */
        private double orderSnapMeters = 250D;

        /**
         * W2-e：画线/仿真的路线规划源。
         *
         * <p>{@code true}（默认）= 先问**现役 DB 路网**（{@code t_road_segment}，446 节点那张图，
         * 边折线已由 §13.90 带进 {@link com.fsd.dispatch.road.ParkRoadGraph}）；
         * 图上跑不通再退回下面两条老路。
         * <p>{@code false} = 退回改之前的行为：只走 {@code pilot_osm_geo.json} 那 30 条折线
         * （bbox 仅 3.195 km²）+ 手工走廊。
         *
         * <p>为什么默认要开：这一路只覆盖 3.195 km²，服务范围里绝大部分位置拿不到真几何，
         * 只能得到空折线的 {@code STRAIGHT_LINE} —— 那正是"路线不沿实路"的成因（§13.89）。
         * 留这个开关是为了**回滚不用改代码**。
         */
        private boolean routeSourceFromGraph = true;

        /** {@code routeSourceFromGraph=true} 时用哪个园区的图规划。演示口径：单园区。 */
        private Long routePlanParkId = 1L;

        /**
         * W2-b：路线门禁总开关。{@code false}（默认）= 一条都不判，行为与今天完全一致。
         * {@code true} 且 {@code routeGateRejects=false} = <b>只度量与告警</b>，先看看会撞上多少条路线。
         */
        private boolean routeGateEnabled = false;

        /**
         * W2-b：真正把线撤掉（返回空折线 ⇒ {@code routeInvalid} ⇒ 地图不画）。
         * 依赖 {@code routeGateEnabled=true} 才生效 —— 拒单是本人看得见的行为，必须两把钥匙一起拧。
         */
        private boolean routeGateRejects = false;
    }

    @Data
    public static class GeoReferencePoint {
        /** Schematic x (与 t_park_station.coord_x 一致) */
        private BigDecimal x;
        /** Schematic y */
        private BigDecimal y;
        /** GCJ-02 经度 */
        private BigDecimal lng;
        /** GCJ-02 纬度 */
        private BigDecimal lat;
    }

    @Data
    public static class StationConfig {

        private Long id;

        private String code;

        private String name;

        private BigDecimal x;

        private BigDecimal y;

        private String area;
    }

    @Data
    public static class PointConfig {

        private String code;

        private BigDecimal x;

        private BigDecimal y;
    }

    @Data
    public static class SimulationConfig {

        private boolean enabled = true;

        /** 叠石桥地理仿真车队数量（ZJF-AV-*）。示意池 PARK-* 已随 §7.6 删除，只剩这一条池。 */
        private int geoVehicleCount = 20;

        private int maxTrailSize = 30;

        private int offlineDurationSeconds = 8;

        private double offlineProbability = 0.005D;

        /** SOC at or below this value is treated as low battery (must charge). */
        private int lowBatteryThreshold = 25;

        /** Vehicles below this SOC are excluded from auto-assign. */
        private int minAssignableBattery = 30;

        /** Target SOC before leaving the charging pile. */
        private int fullChargeLevel = 100;

        /** Battery points recovered per simulation tick while CHARGING. */
        private int chargeRatePerTick = 4;

        /** Minimum displayed SOC while discharging. */
        private int reserveBatteryFloor = 8;

        /** Busy drain: subtract 1% every N movement ticks (1 = every tick). */
        private int busyDrainIntervalTicks = 4;

        /** Idle drain probability per tick (0-1). */
        private double idleDrainProbability = 0.06D;

        /** TO_PICKUP 阶段最大时长（秒），超过后自动转入 EMERGENCY_PARKING。 */
        private int toPickupTimeoutSeconds = 300;
    }

    @Data
    public static class RoadNodeConfig {

        private String code;

        private BigDecimal x;

        private BigDecimal y;
    }

    @Data
    public static class RoadSegmentConfig {

        private String from;

        private String to;
    }
}
