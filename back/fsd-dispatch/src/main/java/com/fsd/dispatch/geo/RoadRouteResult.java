package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.util.List;

/**
 * 路线规划结果（V43 增强：携带路线契约元数据）。
 *
 * <p>新增字段对应 docs/DispatchFlow_最终更新路线图_2026-07-18.md §7.2 结果契约：
 * <ul>
 *   <li>{@code routeId} — 路线唯一标识，用于审计与重规划追溯</li>
 *   <li>{@code mapVersion} — 规划时使用的地图数据版本编码</li>
 *   <li>{@code segmentPath} — 路段编码序列（from&gt;to 节点对），用于绑定执行版本</li>
 *   <li>{@code routeMode} — REAL_ROAD / SCHEMATIC / STRAIGHT_LINE</li>
 *   <li>{@code vehicleFit} — 车辆 profile 是否通过宽度/等级/车型过滤</li>
 *   <li>{@code collisionChecked} — 是否经过两层碰撞校验</li>
 *   <li>{@code reservationStatus} — 服务位/充电桩预约状态</li>
 *   <li>{@code snapDistanceMeters} — 起终点吸附距离</li>
 *   <li>{@code maxOffRoadMeters} — 最大离路距离</li>
 * </ul>
 */
public record RoadRouteResult(
        List<GeoPoint> polyline,
        double distanceMeters,
        RoadRouteSource source,
        boolean invalid,
        boolean crossesBuilding,
        boolean crossesRiver,
        double nearestRoadDistanceMeters,
        // V43 新增契约字段
        String routeId,
        String mapVersion,
        List<String> segmentPath,
        String routeMode,
        boolean vehicleFit,
        boolean collisionChecked,
        String reservationStatus,
        double snapDistanceMeters,
        double maxOffRoadMeters) {

    /** 向后兼容构造：3 参数版本，所有 V43 字段使用默认值。 */
    public RoadRouteResult(List<GeoPoint> polyline, double distanceMeters, RoadRouteSource source) {
        this(polyline, distanceMeters, source, false, false, false, 0D,
                null, null, List.of(), "REAL_ROAD", true, false, null, 0D, 0D);
    }

    /** 向后兼容构造：7 参数版本（含 validation），所有 V43 字段使用默认值。 */
    public RoadRouteResult(List<GeoPoint> polyline, double distanceMeters, RoadRouteSource source,
                           boolean invalid, boolean crossesBuilding, boolean crossesRiver,
                           double nearestRoadDistanceMeters) {
        this(polyline, distanceMeters, source, invalid, crossesBuilding, crossesRiver, nearestRoadDistanceMeters,
                null, null, List.of(), "REAL_ROAD", true, false, null, 0D, 0D);
    }

    public RoadRouteResult {
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
        if (source == null) {
            source = RoadRouteSource.STRAIGHT_LINE;
        }
        if (routeMode == null) {
            routeMode = "REAL_ROAD";
        }
        if (segmentPath == null) {
            segmentPath = List.of();
        }
    }

    public boolean fromAmap() {
        return source == RoadRouteSource.AMAP;
    }

    public boolean fromLocalGraph() {
        return source == RoadRouteSource.LOCAL_GRAPH;
    }

    /** P0-3.1：是否为禁止进入执行队列的回退路线（STRAIGHT_LINE 或空 polyline）。 */
    public boolean isForbiddenFallback() {
        return source == RoadRouteSource.STRAIGHT_LINE || polyline.isEmpty();
    }

    public RoadRouteResult withValidation(RoadRouteValidation validation) {
        if (validation == null) {
            return this;
        }
        return new RoadRouteResult(
                polyline,
                distanceMeters,
                source,
                validation.invalid(),
                validation.crossesBuilding(),
                validation.crossesRiver(),
                validation.nearestRoadDistanceMeters(),
                routeId,
                mapVersion,
                segmentPath,
                routeMode,
                vehicleFit,
                collisionChecked,
                reservationStatus,
                snapDistanceMeters,
                maxOffRoadMeters);
    }

    /** V43：附加路线契约元数据（routeId / mapVersion / segmentPath / routeMode）。 */
    public RoadRouteResult withRouteMetadata(String routeId, String mapVersion,
                                              List<String> segmentPath, String routeMode) {
        return new RoadRouteResult(
                polyline,
                distanceMeters,
                source,
                invalid,
                crossesBuilding,
                crossesRiver,
                nearestRoadDistanceMeters,
                routeId,
                mapVersion,
                segmentPath,
                routeMode,
                vehicleFit,
                collisionChecked,
                reservationStatus,
                snapDistanceMeters,
                maxOffRoadMeters);
    }

    /** V43：附加车辆适配与碰撞校验结果。 */
    public RoadRouteResult withVehicleFit(boolean vehicleFit, boolean collisionChecked) {
        return new RoadRouteResult(
                polyline,
                distanceMeters,
                source,
                invalid,
                crossesBuilding,
                crossesRiver,
                nearestRoadDistanceMeters,
                routeId,
                mapVersion,
                segmentPath,
                routeMode,
                vehicleFit,
                collisionChecked,
                reservationStatus,
                snapDistanceMeters,
                maxOffRoadMeters);
    }

    /** V43：附加吸附距离与最大离路距离。 */
    public RoadRouteResult withSnapAndOffRoad(double snapDistanceMeters, double maxOffRoadMeters) {
        return new RoadRouteResult(
                polyline,
                distanceMeters,
                source,
                invalid,
                crossesBuilding,
                crossesRiver,
                nearestRoadDistanceMeters,
                routeId,
                mapVersion,
                segmentPath,
                routeMode,
                vehicleFit,
                collisionChecked,
                reservationStatus,
                snapDistanceMeters,
                maxOffRoadMeters);
    }
}
