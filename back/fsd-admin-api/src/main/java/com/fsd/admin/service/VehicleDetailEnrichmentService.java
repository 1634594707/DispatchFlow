package com.fsd.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.entity.RouteAuditEntity;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.mapper.RouteAuditMapper;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 车辆详情路线上下文 enrichment（路线图 5.2）。
 *
 * <p>为车辆详情补充：最近一次路线执行的 routeId / 地图版本 / 偏航距离 / 执行时间，
 * 以及基于当前位置的最近道路节点（50m 内视为在道路上）。</p>
 */
@Service
public class VehicleDetailEnrichmentService {

    private static final double ROAD_MATCH_THRESHOLD_METERS = 50D;

    private final RouteAuditMapper routeAuditMapper;
    private final RoadNodeMapper roadNodeMapper;

    public VehicleDetailEnrichmentService(RouteAuditMapper routeAuditMapper, RoadNodeMapper roadNodeMapper) {
        this.routeAuditMapper = routeAuditMapper;
        this.roadNodeMapper = roadNodeMapper;
    }

    /** 就地填充路线执行上下文字段；任何缺失数据保持 null，不影响原详情。 */
    public void enrich(Long vehicleId, VehicleAdminDetailResponse detail) {
        if (detail == null) {
            return;
        }
        enrichLatestRouteAudit(vehicleId, detail);
        enrichCurrentRoad(detail);
    }

    private void enrichLatestRouteAudit(Long vehicleId, VehicleAdminDetailResponse detail) {
        RouteAuditEntity latest = routeAuditMapper.selectOne(new LambdaQueryWrapper<RouteAuditEntity>()
                .eq(RouteAuditEntity::getVehicleId, vehicleId)
                .eq(RouteAuditEntity::getDeleted, 0)
                .orderByDesc(RouteAuditEntity::getExecutedAt)
                .last("LIMIT 1"));
        if (latest == null) {
            return;
        }
        detail.setRouteAuditRouteId(latest.getRouteId());
        detail.setRouteMapVersion(latest.getMapVersionCode());
        detail.setRouteDeviationMeters(latest.getDeviationMeters());
        detail.setRouteExecutedAt(latest.getExecutedAt() != null ? latest.getExecutedAt() : latest.getCreatedAt());
    }

    private void enrichCurrentRoad(VehicleAdminDetailResponse detail) {
        if (detail.getCurrentLongitude() == null || detail.getCurrentLatitude() == null || detail.getParkId() == null) {
            return;
        }
        List<RoadNodeEntity> nodes = roadNodeMapper.selectList(new LambdaQueryWrapper<RoadNodeEntity>()
                .eq(RoadNodeEntity::getParkId, detail.getParkId())
                .eq(RoadNodeEntity::getStatus, "ACTIVE")
                .isNotNull(RoadNodeEntity::getCoordLng)
                .isNotNull(RoadNodeEntity::getCoordLat));
        GeoPoint position = new GeoPoint(detail.getCurrentLongitude(), detail.getCurrentLatitude());
        RoadNodeEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (RoadNodeEntity node : nodes) {
            GeoPoint nodePoint = new GeoPoint(node.getCoordLng(), node.getCoordLat());
            double distance = GeoPolygonUtils.haversineMeters(position, nodePoint);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = node;
            }
        }
        if (nearest != null && nearestDistance <= ROAD_MATCH_THRESHOLD_METERS) {
            detail.setCurrentRoadNodeCode(nearest.getNodeCode());
        }
    }
}