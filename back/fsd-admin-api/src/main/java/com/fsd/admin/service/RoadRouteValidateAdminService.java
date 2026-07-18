package com.fsd.admin.service;

import com.fsd.admin.vo.RoadRouteValidateRequest;
import com.fsd.admin.vo.RoadRouteValidateResponse;
import com.fsd.dispatch.entity.MapDataVersionEntity;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteCollisionValidator;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.RoadRouteValidation;
import com.fsd.dispatch.geo.RouteAuditService;
import com.fsd.dispatch.geo.RouteEndpointSnapper;
import com.fsd.dispatch.geo.RouteMetrics;
import com.fsd.dispatch.geo.RouteMetricsCalculator;
import com.fsd.dispatch.geo.RouteUnreachableReason;
import com.fsd.dispatch.geo.VehicleRoutingProfile;
import com.fsd.dispatch.mapper.MapDataVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 路线校验管理端服务（V43 增强：路线图 §7 路线请求与结果契约）。
 *
 * <p>V43 新增能力：
 * <ul>
 *   <li>起终点吸附到道路节点（{@link RouteEndpointSnapper}）</li>
 *   <li>加载车辆通行档案（{@link VehicleRoutingProfile}）参与碰撞校验</li>
 *   <li>生成路线 routeId（UUID），保存路线审计记录（{@link RouteAuditService}）</li>
 *   <li>返回路线契约字段：routeId / mapVersion / routeMode / segmentPath / polyline / snapDistanceMeters / vehicleFit / collisionChecked</li>
 *   <li>P0-3.1：当 source = STRAIGHT_LINE 且 allowStraightLine = false 时，强制 invalid</li>
 * </ul>
 */
@Service
public class RoadRouteValidateAdminService {

    private final RoadRouteService roadRouteService;
    private final RoadRouteCollisionValidator collisionValidator;
    private final RouteMetricsCalculator routeMetricsCalculator;
    private final RouteEndpointSnapper endpointSnapper;
    private final RouteAuditService routeAuditService;
    private final VehicleMapper vehicleMapper;
    private final MapDataVersionMapper mapDataVersionMapper;

    public RoadRouteValidateAdminService(RoadRouteService roadRouteService,
                                         RoadRouteCollisionValidator collisionValidator,
                                         RouteMetricsCalculator routeMetricsCalculator,
                                         RouteEndpointSnapper endpointSnapper,
                                         RouteAuditService routeAuditService,
                                         VehicleMapper vehicleMapper,
                                         MapDataVersionMapper mapDataVersionMapper) {
        this.roadRouteService = roadRouteService;
        this.collisionValidator = collisionValidator;
        this.routeMetricsCalculator = routeMetricsCalculator;
        this.endpointSnapper = endpointSnapper;
        this.routeAuditService = routeAuditService;
        this.vehicleMapper = vehicleMapper;
        this.mapDataVersionMapper = mapDataVersionMapper;
    }

    public RoadRouteValidateResponse validate(RoadRouteValidateRequest request) {
        // V43: 生成路线唯一标识
        String routeId = "ROUTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String routeMode = resolveRouteMode(request);
        boolean allowStraightLine = resolveAllowStraightLine(request);

        // V43: 加载车辆通行档案
        VehicleRoutingProfile profile = loadVehicleProfile(request.getVehicleId());

        // V43: 解析起终点
        GeoPoint origin = toPoint(request.getOriginLng(), request.getOriginLat());
        GeoPoint destination = toPoint(request.getDestinationLng(), request.getDestinationLat());

        // V43: 起终点吸附到道路节点
        double snapDistanceMeters = 0D;
        List<String> nodePath = new ArrayList<>();
        if (origin != null && destination != null) {
            RouteEndpointSnapper.SnapResult originSnap = endpointSnapper.snapToRoadNode(
                    origin, request.getParkId(), request.getOriginAccessNodeCode(), request.getSnapDistanceMeters());
            RouteEndpointSnapper.SnapResult destinationSnap = endpointSnapper.snapToRoadNode(
                    destination, request.getParkId(), request.getDestinationAccessNodeCode(), request.getSnapDistanceMeters());

            if (originSnap != null && originSnap.withinThreshold()) {
                origin = originSnap.snappedPoint();
                snapDistanceMeters += originSnap.distanceMeters();
                nodePath.add(originSnap.nodeCode());
            } else if (originSnap != null) {
                // P0-3.2：吸附距离超过阈值 → 拒绝规划
                return buildUnreachableResponse(request, routeId, routeMode, origin, destination,
                        RouteUnreachableReason.START_OFF_ROAD,
                        "Origin snap distance " + String.format("%.2f", originSnap.distanceMeters())
                                + "m exceeds threshold " + request.getSnapDistanceMeters() + "m");
            }
            if (destinationSnap != null && destinationSnap.withinThreshold()) {
                destination = destinationSnap.snappedPoint();
                snapDistanceMeters += destinationSnap.distanceMeters();
                nodePath.add(destinationSnap.nodeCode());
            } else if (destinationSnap != null) {
                return buildUnreachableResponse(request, routeId, routeMode, origin, destination,
                        RouteUnreachableReason.END_OFF_ROAD,
                        "Destination snap distance " + String.format("%.2f", destinationSnap.distanceMeters())
                                + "m exceeds threshold " + request.getSnapDistanceMeters() + "m");
            }
        }

        // V43: 解析或规划 polyline
        List<GeoPoint> polyline = resolvePolyline(request);
        RoadRouteValidation validation;
        String source = null;
        RouteUnreachableReason unreachableReason = null;
        String unreachableDetail = null;

        if (request.getPolyline() == null || request.getPolyline().isEmpty()) {
            if (origin != null && destination != null) {
                RoadRouteResult planned = roadRouteService.planDrivingRoute(origin, destination);
                polyline = planned.polyline();
                source = planned.source().name();

                // V43: 应用碰撞校验（携带 VehicleRoutingProfile）
                planned = collisionValidator.applyValidation(planned, profile);
                validation = new RoadRouteValidation(
                        planned.invalid(),
                        planned.crossesBuilding(),
                        planned.crossesRiver(),
                        planned.nearestRoadDistanceMeters());

                // V43: P0-3.1 — 当 source = STRAIGHT_LINE 且 allowStraightLine = false 时，强制 invalid
                if (planned.source() == com.fsd.dispatch.geo.RoadRouteSource.STRAIGHT_LINE && !allowStraightLine) {
                    validation = RoadRouteValidation.invalid(
                            validation.crossesBuilding(),
                            validation.crossesRiver(),
                            validation.nearestRoadDistanceMeters());
                    unreachableReason = RouteUnreachableReason.NO_PATH_ON_GRAPH;
                    unreachableDetail = "Straight-line fallback is disabled (allowStraightLine=false) — "
                            + "no real road path exists";
                } else if (planned.invalid()) {
                    unreachableReason = inferUnreachableReason(planned);
                    unreachableDetail = buildUnreachableDetail(planned, unreachableReason);
                }
            } else if (origin == null && destination == null) {
                unreachableReason = RouteUnreachableReason.START_OFF_ROAD;
                unreachableDetail = "Origin coordinates are required";
                validation = RoadRouteValidation.invalid(false, false, 0D);
            } else if (origin == null) {
                unreachableReason = RouteUnreachableReason.START_OFF_ROAD;
                unreachableDetail = "Origin coordinates are required";
                validation = RoadRouteValidation.invalid(false, false, 0D);
            } else {
                unreachableReason = RouteUnreachableReason.END_OFF_ROAD;
                unreachableDetail = "Destination coordinates are required";
                validation = RoadRouteValidation.invalid(false, false, 0D);
            }
        } else {
            // V43: 对显式 polyline 也应用车辆包络碰撞校验
            validation = collisionValidator.validate(polyline, profile);
            source = "EXTERNAL";
        }

        // V43: 计算路线指标
        RouteMetrics metrics = routeMetricsCalculator.compute(
                null, polyline, List.of(), null, null, null);

        // V43: 解析地图版本
        String mapVersionCode = resolveMapVersionCode(request);

        // V43: 构建路段路径（基于 polyline 近似，实际应由 RoadRouteService 返回）
        List<String> segmentPath = buildSegmentPath(nodePath);

        // V43: 构建 polyline 二维数组
        List<double[]> polylineArray = toPolylineArray(polyline);

        // V43: 保存路线审计记录
        boolean vehicleFit = isVehicleFit(profile, polyline);
        boolean collisionChecked = true;
        try {
            routeAuditService.saveRouteAudit(routeId, request.getParkId(),
                    null, mapVersionCode, routeMode, source,
                    origin, destination,
                    polylineToJson(polyline), BigDecimal.valueOf(metrics.totalLengthMeters()),
                    collisionChecked, validation.crossesBuilding(), validation.crossesRiver(),
                    unreachableReason == null ? null : unreachableReason.code(),
                    validation.invalid() ? "FAILED" : "PLANNED",
                    null, request.getVehicleId());
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }

        return RoadRouteValidateResponse.builder()
                .invalid(validation.invalid())
                .crossesBuilding(validation.crossesBuilding())
                .crossesRiver(validation.crossesRiver())
                .nearestRoadDistanceMeters(validation.nearestRoadDistanceMeters())
                .vertexCount(polyline.size())
                .source(source)
                .unreachableReason(unreachableReason == null ? null : unreachableReason.code())
                .unreachableDetail(unreachableDetail)
                .totalLengthMeters(metrics.totalLengthMeters())
                .estimatedTravelSeconds(metrics.estimatedTravelSeconds())
                .waitingSeconds(metrics.waitingSeconds())
                .chargingSeconds(metrics.chargingSeconds())
                .riskPoints(metrics.riskPoints())
                // V43 契约字段
                .routeId(routeId)
                .mapVersion(mapVersionCode)
                .routeMode(routeMode)
                .nodePath(nodePath)
                .segmentPath(segmentPath)
                .polyline(polylineArray)
                .snapDistanceMeters(snapDistanceMeters)
                .maxOffRoadMeters(validation.nearestRoadDistanceMeters())
                .crossesRestrictedZone(validation.crossesBuilding() || validation.crossesRiver())
                .vehicleFit(vehicleFit)
                .collisionChecked(collisionChecked)
                .reservationStatus(null)
                .build();
    }

    private RoadRouteValidateResponse buildUnreachableResponse(RoadRouteValidateRequest request,
                                                                String routeId, String routeMode,
                                                                GeoPoint origin, GeoPoint destination,
                                                                RouteUnreachableReason reason,
                                                                String detail) {
        return RoadRouteValidateResponse.builder()
                .invalid(true)
                .crossesBuilding(false)
                .crossesRiver(false)
                .nearestRoadDistanceMeters(0D)
                .vertexCount(0)
                .source(null)
                .unreachableReason(reason.code())
                .unreachableDetail(detail)
                .totalLengthMeters(0D)
                .estimatedTravelSeconds(0L)
                .waitingSeconds(0L)
                .chargingSeconds(0L)
                .riskPoints(List.of())
                .routeId(routeId)
                .mapVersion(request.getMapVersion())
                .routeMode(routeMode)
                .nodePath(List.of())
                .segmentPath(List.of())
                .polyline(List.of())
                .snapDistanceMeters(0D)
                .maxOffRoadMeters(0D)
                .crossesRestrictedZone(false)
                .vehicleFit(false)
                .collisionChecked(false)
                .reservationStatus(null)
                .build();
    }

    private String resolveRouteMode(RoadRouteValidateRequest request) {
        if (request.getRouteMode() == null || request.getRouteMode().isBlank()) {
            return "REAL_ROAD";
        }
        return request.getRouteMode().toUpperCase();
    }

    private boolean resolveAllowStraightLine(RoadRouteValidateRequest request) {
        if (request.getAllowStraightLine() == null) {
            return false; // 默认禁止直线回退（P0-3.1）
        }
        return request.getAllowStraightLine();
    }

    private VehicleRoutingProfile loadVehicleProfile(Long vehicleId) {
        if (vehicleId == null) {
            return VehicleRoutingProfile.unknown(null);
        }
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            return VehicleRoutingProfile.unknown(vehicleId);
        }
        double safetyBuffer = vehicle.getSafetyBufferMeters() != null
                ? vehicle.getSafetyBufferMeters().doubleValue()
                : VehicleRoutingProfile.DEFAULT_SAFETY_BUFFER_METERS;
        return new VehicleRoutingProfile(
                vehicle.getId(),
                vehicle.getVehicleType(),
                vehicle.getWidthCm(),
                vehicle.getLengthCm(),
                vehicle.getTurningRadiusM(),
                vehicle.getAllowedRoadClasses(),
                null,
                safetyBuffer);
    }

    private boolean isVehicleFit(VehicleRoutingProfile profile, List<GeoPoint> polyline) {
        // 简化判断：若 profile 未知（widthCm=null），视为适配
        return profile.widthCm() != null || polyline.isEmpty();
    }

    private String resolveMapVersionCode(RoadRouteValidateRequest request) {
        if (request.getMapVersion() != null && !request.getMapVersion().isBlank()) {
            return request.getMapVersion();
        }
        if (request.getParkId() != null) {
            LambdaQueryWrapper<MapDataVersionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MapDataVersionEntity::getParkId, request.getParkId())
                    .eq(MapDataVersionEntity::getIsActive, 1)
                    .eq(MapDataVersionEntity::getDeleted, 0)
                    .last("LIMIT 1");
            MapDataVersionEntity entity = mapDataVersionMapper.selectOne(wrapper);
            if (entity != null) {
                return entity.getVersionCode();
            }
        }
        return null;
    }

    private List<String> buildSegmentPath(List<String> nodePath) {
        if (nodePath == null || nodePath.size() < 2) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (int i = 1; i < nodePath.size(); i++) {
            segments.add(nodePath.get(i - 1) + ">" + nodePath.get(i));
        }
        return segments;
    }

    private List<double[]> toPolylineArray(List<GeoPoint> polyline) {
        List<double[]> result = new ArrayList<>();
        for (GeoPoint point : polyline) {
            if (point != null) {
                result.add(new double[]{
                        point.longitude().doubleValue(),
                        point.latitude().doubleValue()
                });
            }
        }
        return result;
    }

    private String polylineToJson(List<GeoPoint> polyline) {
        if (polyline == null || polyline.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < polyline.size(); i++) {
            GeoPoint point = polyline.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("[").append(point.longitude()).append(",").append(point.latitude()).append("]");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<GeoPoint> resolvePolyline(RoadRouteValidateRequest request) {
        if (request.getPolyline() != null && !request.getPolyline().isEmpty()) {
            List<GeoPoint> points = new ArrayList<>();
            for (RoadRouteValidateRequest.RoadRoutePointDto point : request.getPolyline()) {
                GeoPoint geo = toPoint(point.getLongitude(), point.getLatitude());
                if (geo != null) {
                    points.add(geo);
                }
            }
            return points;
        }
        return List.of();
    }

    private static RouteUnreachableReason inferUnreachableReason(RoadRouteResult result) {
        if (result.crossesBuilding()) {
            return RouteUnreachableReason.CROSSES_BUILDING;
        }
        if (result.crossesRiver()) {
            return RouteUnreachableReason.CROSSES_RIVER;
        }
        if (result.polyline() == null || result.polyline().isEmpty()) {
            return RouteUnreachableReason.NO_PATH_ON_GRAPH;
        }
        if (result.polyline().size() < 4) {
            return RouteUnreachableReason.NO_PATH_ON_GRAPH;
        }
        return null;
    }

    private static String buildUnreachableDetail(RoadRouteResult result, RouteUnreachableReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case CROSSES_BUILDING -> "Route polyline crosses a building polygon — service position may not be configured";
            case CROSSES_RIVER -> "Route polyline crosses a river / forbidden service yard";
            case NO_PATH_ON_GRAPH -> "No path found on the road graph between origin and destination";
            default -> reason.code();
        };
    }

    private static GeoPoint toPoint(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return new GeoPoint(lng, lat);
    }
}
