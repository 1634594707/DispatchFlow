package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 路线校验响应（V43 增强：路线图 §7.2 结果契约）。
 *
 * <p>新增字段：
 * <ul>
 *   <li>{@code routeId} — 路线唯一标识</li>
 *   <li>{@code mapVersion} — 规划时使用的地图版本</li>
 *   <li>{@code source} — 路线来源：AMAP/LOCAL_GRAPH/STRAIGHT_LINE</li>
 *   <li>{@code routeMode} — REAL_ROAD / SCHEMATIC / STRAIGHT_LINE</li>
 *   <li>{@code nodePath} — 节点编码路径</li>
 *   <li>{@code segmentPath} — 路段编码路径</li>
 *   <li>{@code polyline} — GCJ-02 坐标点列表</li>
 *   <li>{@code snapDistanceMeters} — 起终点吸附距离</li>
 *   <li>{@code maxOffRoadMeters} — 最大离路距离</li>
 *   <li>{@code crossesRestrictedZone} — 是否穿越受限区</li>
 *   <li>{@code vehicleFit} — 车辆 profile 是否适配</li>
 *   <li>{@code collisionChecked} — 是否经过碰撞校验</li>
 *   <li>{@code reservationStatus} — 服务位/充电桩预约状态</li>
 *   <li>{@code invalid} — 是否不可执行</li>
 *   <li>{@code unreachableReason} — 不可达原因编码</li>
 *   <li>{@code unreachableDetail} — 不可达原因详情</li>
 *   <li>{@code totalLengthMeters} — 路线总长（米）</li>
 *   <li>{@code estimatedTravelSeconds} — 预计行驶时间（秒）</li>
 *   <li>{@code waitingSeconds} — 等待时间（秒）</li>
 *   <li>{@code chargingSeconds} — 充电时间（秒）</li>
 *   <li>{@code riskPoints} — 风险点列表</li>
 * </ul>
 */
@Data
@Builder
public class RoadRouteValidateResponse {

    // ===== 原有字段 =====

    private boolean invalid;

    private boolean crossesBuilding;

    private boolean crossesRiver;

    private double nearestRoadDistanceMeters;

    private int vertexCount;

    private String source;

    /** P1-4: granular unreachable reason code (null = reachable). */
    private String unreachableReason;

    /** P1-4: human-readable detail for the unreachable reason. */
    private String unreachableDetail;

    /** P1-5: total route length in meters. */
    private double totalLengthMeters;

    /** P1-5: estimated travel time in seconds (based on per-segment speed limits). */
    private long estimatedTravelSeconds;

    /** P1-5: waiting time at service positions in seconds. */
    private long waitingSeconds;

    /** P1-5: charging time in seconds (if route includes a charging stop). */
    private long chargingSeconds;

    /** P1-5: risk points along the route (node pairs with gates / NO_STOP / etc). */
    private List<String> riskPoints;

    // ===== V43 新增契约字段（路线图 §7.2） =====

    /** V43: 路线唯一标识（UUID），用于审计与重规划追溯 */
    private String routeId;

    /** V43: 规划时使用的地图数据版本编码 */
    private String mapVersion;

    /** V43: 路线模式：REAL_ROAD / SCHEMATIC / STRAIGHT_LINE */
    private String routeMode;

    /** V43: 节点编码路径（从起点到终点经过的道路节点编码序列） */
    private List<String> nodePath;

    /** V43: 路段编码路径（从起点到终点经过的路段编码序列，格式 fromCode>toCode） */
    private List<String> segmentPath;

    /** V43: GCJ-02 坐标点列表（[lng, lat] 二维数组） */
    private List<double[]> polyline;

    /** V43: 起终点吸附距离（米），起终点吸附到道路接入点的距离 */
    private double snapDistanceMeters;

    /** V43: 最大离路距离（米），路线中心线偏离道路的最大距离 */
    private double maxOffRoadMeters;

    /** V43: 是否穿越受限区（门禁/消防通道/施工区） */
    private boolean crossesRestrictedZone;

    /** V43: 车辆 profile 是否适配（车辆宽度/等级/车型是否通过过滤） */
    private boolean vehicleFit;

    /** V43: 是否经过两层碰撞校验（中心线 + 车辆包络） */
    private boolean collisionChecked;

    /** V43: 服务位/充电桩预约状态：FREE/RESERVED/LOCKED/OCCUPIED */
    private String reservationStatus;
}
