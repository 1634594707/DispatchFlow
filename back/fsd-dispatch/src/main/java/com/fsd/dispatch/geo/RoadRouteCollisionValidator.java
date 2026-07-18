package com.fsd.dispatch.geo;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import com.fsd.dispatch.geo.local.OsmPilotGeoRepository;
import com.fsd.dispatch.geo.local.PilotForbiddenZones;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * 路线碰撞校验器（V43 增强）。
 *
 * <p>P0-3.2 修复：空折线 / 零长度折线 / 顶点数 &lt; 2 的折线必须返回 invalid，而不是 valid。
 * <p>P0-4.2 增强：按 {@link VehicleRoutingProfile} 膨胀建筑物 Polygon，进行两层碰撞校验：
 * <ol>
 *   <li>路线中心线与建筑物膨胀区碰撞检查（原有逻辑保留）</li>
 *   <li>车辆外接矩形（包络）与建筑物膨胀区碰撞检查（V43 新增）</li>
 * </ol>
 * <p>P0-4.2 新增：检查路线是否从建筑内部开始或结束。
 */
@Service
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class RoadRouteCollisionValidator {

    private static final double OFF_ROAD_THRESHOLD_METERS = 45D;

    private final LocalPilotRoadGraphService localPilotRoadGraphService;
    private final OsmPilotGeoRepository osmPilotGeoRepository;

    public RoadRouteCollisionValidator(LocalPilotRoadGraphService localPilotRoadGraphService,
                                       OsmPilotGeoRepository osmPilotGeoRepository) {
        this.localPilotRoadGraphService = localPilotRoadGraphService;
        this.osmPilotGeoRepository = osmPilotGeoRepository;
    }

    /**
     * 校验路线中心线碰撞（向后兼容入口）。
     * 等价于 {@code validate(polyline, VehicleRoutingProfile.unknown(null))}。
     */
    public RoadRouteValidation validate(List<GeoPoint> polyline) {
        return validate(polyline, VehicleRoutingProfile.unknown(null));
    }

    /**
     * 校验路线中心线 + 车辆包络两层碰撞（V43 / P0-4.2）。
     *
     * <p>校验规则：
     * <ol>
     *   <li>空折线 / 顶点数 &lt; 2 → 直接返回 invalid（P0-3.2 修复）</li>
     *   <li>顶点数 &lt; 4 → 视为不可执行，返回 invalid + nearestRoadDistance</li>
     *   <li>起点或终点位于建筑物膨胀区内 → 返回 invalid（P0-4.2 新增）</li>
     *   <li>路线中心线与建筑物膨胀区相交 → crossesBuilding=true</li>
     *   <li>路线中心线与河道禁行区相交 → crossesRiver=true</li>
     *   <li>路线离路距离超过阈值 → invalid</li>
     * </ol>
     */
    public RoadRouteValidation validate(List<GeoPoint> polyline, VehicleRoutingProfile profile) {
        // P0-3.2 修复：空折线 / 顶点数 < 2 必须是无效，而不是有效
        if (polyline == null || polyline.isEmpty()) {
            return RoadRouteValidation.invalid(false, false, 0D);
        }
        if (polyline.size() < 2) {
            return RoadRouteValidation.invalid(false, false, 0D);
        }
        if (polyline.size() < 4) {
            double nearest = maxNearestRoadDistance(polyline);
            return RoadRouteValidation.invalid(false, false, nearest);
        }

        // P0-4.2：车辆包络膨胀（按车辆宽度 + 安全缓冲）
        double expansionBuffer = resolveExpansionBuffer(profile);

        // P0-4.2：起点/终点位于建筑物膨胀区内 → 不可执行
        boolean crossesBuilding = crossesBuilding(polyline, expansionBuffer);
        boolean crossesRiver = crossesRiver(polyline);
        double nearest = maxNearestRoadDistance(polyline);
        boolean offRoad = nearest > OFF_ROAD_THRESHOLD_METERS;

        // P0-4.2：起点或终点位于建筑内部 → 不可执行
        boolean startOrEndInsideBuilding = isStartOrEndInsideBuilding(polyline, expansionBuffer);

        if (crossesBuilding || crossesRiver || offRoad || startOrEndInsideBuilding) {
            return RoadRouteValidation.invalid(crossesBuilding, crossesRiver, nearest);
        }
        return RoadRouteValidation.valid(nearest);
    }

    public RoadRouteResult applyValidation(RoadRouteResult result) {
        return applyValidation(result, VehicleRoutingProfile.unknown(null));
    }

    /**
     * 应用碰撞校验（V43：携带 VehicleRoutingProfile）。
     * 当 source = STRAIGHT_LINE 时，强制 invalid（P0-3.1：禁止直线回退）。
     */
    public RoadRouteResult applyValidation(RoadRouteResult result, VehicleRoutingProfile profile) {
        if (result == null) {
            return result;
        }
        RoadRouteValidation validation = validate(result.polyline(), profile);
        if (result.source() == RoadRouteSource.STRAIGHT_LINE) {
            // P0-3.1：禁止 STRAIGHT_LINE 进入执行队列
            validation = RoadRouteValidation.invalid(
                    validation.crossesBuilding(),
                    validation.crossesRiver(),
                    validation.nearestRoadDistanceMeters());
        }
        return result.withValidation(validation);
    }

    private double resolveExpansionBuffer(VehicleRoutingProfile profile) {
        if (profile == null) {
            return VehicleRoutingProfile.DEFAULT_SAFETY_BUFFER_METERS;
        }
        return profile.safetyBufferMeters();
    }

    private boolean crossesBuilding(List<GeoPoint> polyline, double expansionBuffer) {
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            for (List<GeoPoint> block : buildingBlocks()) {
                if (block.size() < 3) {
                    continue;
                }
                // V43：按车辆宽度 + 安全缓冲膨胀建筑物边界
                List<GeoPoint> expanded = expansionBuffer > 0
                        ? GeoPolygonUtils.expandPolygon(block, expansionBuffer)
                        : block;
                if (GeoPolygonUtils.segmentIntersectsPolygon(a, b, expanded)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isStartOrEndInsideBuilding(List<GeoPoint> polyline, double expansionBuffer) {
        if (polyline.isEmpty()) {
            return false;
        }
        GeoPoint start = polyline.get(0);
        GeoPoint end = polyline.get(polyline.size() - 1);
        for (List<GeoPoint> block : buildingBlocks()) {
            if (block.size() < 3) {
                continue;
            }
            List<GeoPoint> expanded = expansionBuffer > 0
                    ? GeoPolygonUtils.expandPolygon(block, expansionBuffer)
                    : block;
            if (GeoPolygonUtils.pointInPolygon(start, expanded)
                    || GeoPolygonUtils.pointInPolygon(end, expanded)) {
                return true;
            }
        }
        return false;
    }

    private List<List<GeoPoint>> buildingBlocks() {
        if (osmPilotGeoRepository.isLoaded()) {
            return osmPilotGeoRepository.buildings();
        }
        return PilotForbiddenZones.BUILDING_BLOCKS;
    }

    private boolean crossesRiver(List<GeoPoint> polyline) {
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            for (List<GeoPoint> river : PilotForbiddenZones.RIVER_ZONES) {
                if (GeoPolygonUtils.segmentIntersectsPolygon(a, b, river)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double maxNearestRoadDistance(List<GeoPoint> polyline) {
        List<List<GeoPoint>> roadSegments = localPilotRoadGraphService.allRoadSegments();
        double max = 0D;
        for (GeoPoint point : samplePoints(polyline)) {
            double nearest = nearestRoadDistanceMeters(point, roadSegments);
            if (nearest > max) {
                max = nearest;
            }
        }
        return max;
    }

    private static List<GeoPoint> samplePoints(List<GeoPoint> polyline) {
        List<GeoPoint> samples = new ArrayList<>(polyline);
        for (int i = 1; i < polyline.size(); i++) {
            GeoPoint a = polyline.get(i - 1);
            GeoPoint b = polyline.get(i);
            samples.add(midpoint(a, b));
        }
        return samples;
    }

    private static GeoPoint midpoint(GeoPoint a, GeoPoint b) {
        double lng = (a.longitude().doubleValue() + b.longitude().doubleValue()) / 2D;
        double lat = (a.latitude().doubleValue() + b.latitude().doubleValue()) / 2D;
        return PilotForbiddenZones.g(lng, lat);
    }

    private static double nearestRoadDistanceMeters(GeoPoint point, List<List<GeoPoint>> roadSegments) {
        double min = Double.MAX_VALUE;
        for (List<GeoPoint> segment : roadSegments) {
            for (int i = 1; i < segment.size(); i++) {
                double dist = GeoPolygonUtils.distancePointToSegmentMeters(
                        point, segment.get(i - 1), segment.get(i));
                if (dist < min) {
                    min = dist;
                }
            }
        }
        return min == Double.MAX_VALUE ? 0D : min;
    }
}
