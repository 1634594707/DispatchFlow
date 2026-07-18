package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 路线校验请求（V43 增强：路线图 §7.1 请求契约）。
 *
 * <p>新增字段：
 * <ul>
 *   <li>{@code parkId} — 园区ID，用于加载对应路网</li>
 *   <li>{@code mapVersion} — 地图数据版本编码</li>
 *   <li>{@code coordSystem} — 坐标系：GCJ02 / WGS84</li>
 *   <li>{@code originType} — 起点类型：VEHICLE_POSITION / SERVICE_POSITION / ROAD_NODE</li>
 *   <li>{@code destinationType} — 终点类型：PICKUP / DELIVERY / CHARGING / PARKING / SERVICE_POSITION</li>
 *   <li>{@code routeMode} — 路线模式：REAL_ROAD / SCHEMATIC / STRAIGHT_LINE</li>
 *   <li>{@code allowStraightLine} — 是否允许直线回退（false=禁止，P0-3.1）</li>
 *   <li>{@code avoidBuilding} — 是否避让建筑物</li>
 *   <li>{@code avoidRiver} — 是否避让河道</li>
 *   <li>{@code avoidPedestrianZone} — 是否避让步行区</li>
 *   <li>{@code snapDistanceMeters} — 起终点吸附阈值（米）</li>
 *   <li>{@code vehicleId} — 车辆ID，用于加载 VehicleRoutingProfile</li>
 *   <li>{@code originAccessNodeCode} — 起点接入道路节点编码</li>
 *   <li>{@code destinationAccessNodeCode} — 终点接入道路节点编码</li>
 * </ul>
 */
@Data
public class RoadRouteValidateRequest {

    private BigDecimal originLng;

    private BigDecimal originLat;

    private BigDecimal destinationLng;

    private BigDecimal destinationLat;

    /** Optional explicit polyline (GCJ-02). When empty, plans route from origin/destination. */
    private List<RoadRoutePointDto> polyline;

    // ===== V43 路线契约字段（路线图 §7.1） =====

    /** 园区ID，用于加载对应路网 */
    private Long parkId;

    /** 地图数据版本编码 */
    private String mapVersion;

    /** 坐标系：GCJ02 / WGS84（默认 GCJ02） */
    private String coordSystem;

    /** 起点类型：VEHICLE_POSITION / SERVICE_POSITION / ROAD_NODE */
    private String originType;

    /** 终点类型：PICKUP / DELIVERY / CHARGING / PARKING / SERVICE_POSITION */
    private String destinationType;

    /** 路线模式：REAL_ROAD / SCHEMATIC / STRAIGHT_LINE（默认 REAL_ROAD） */
    private String routeMode;

    /** 是否允许直线回退（false=禁止 STRAIGHT_LINE 进入执行队列，P0-3.1） */
    private Boolean allowStraightLine;

    /** 是否避让建筑物（默认 true） */
    private Boolean avoidBuilding;

    /** 是否避让河道（默认 true） */
    private Boolean avoidRiver;

    /** 是否避让步行区（默认 true） */
    private Boolean avoidPedestrianZone;

    /** 起终点吸附阈值（米），超过则拒绝规划 */
    private Double snapDistanceMeters;

    /** 车辆ID，用于加载 VehicleRoutingProfile */
    private Long vehicleId;

    /** 起点接入道路节点编码（若已知） */
    private String originAccessNodeCode;

    /** 终点接入道路节点编码（若已知） */
    private String destinationAccessNodeCode;

    @Data
    public static class RoadRoutePointDto {
        private BigDecimal longitude;
        private BigDecimal latitude;
    }
}
