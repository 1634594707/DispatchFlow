package com.fsd.dispatch.geo;

import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * 起终点吸附服务（V43 / P0-3.2）。
 *
 * <p>将起点/终点吸附到最近的道路节点或服务位接入边，避免使用原始坐标导致路线从建筑内部开始。
 *
 * <p>吸附规则：
 * <ol>
 *   <li>若请求已指定 originAccessNodeCode / destinationAccessNodeCode，直接使用该节点</li>
 *   <li>否则在园区内 ACTIVE 道路节点中查找最近的节点（haversine 距离）</li>
 *   <li>若最近距离超过 snapDistanceMeters 阈值，返回 null 表示无法吸附</li>
 * </ol>
 */
@Service
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class RouteEndpointSnapper {

    private static final double DEFAULT_SNAP_THRESHOLD_METERS = 50D;

    private final RoadNodeMapper roadNodeMapper;

    public RouteEndpointSnapper(RoadNodeMapper roadNodeMapper) {
        this.roadNodeMapper = roadNodeMapper;
    }

    /**
     * 将坐标点吸附到最近的道路节点。
     *
     * @param point 原始坐标点（GCJ-02）
     * @param parkId 园区ID
     * @param accessNodeCode 已知接入节点编码（可选，若非 null 则直接返回该节点）
     * @param snapDistanceMeters 吸附阈值（米），null 则使用默认值 50m
     * @return 吸附结果；若无法吸附返回 null
     */
    public SnapResult snapToRoadNode(GeoPoint point, Long parkId, String accessNodeCode,
                                      Double snapDistanceMeters) {
        if (point == null) {
            return null;
        }

        double threshold = snapDistanceMeters != null ? snapDistanceMeters : DEFAULT_SNAP_THRESHOLD_METERS;

        // 1. 若已指定接入节点，直接使用
        if (accessNodeCode != null && !accessNodeCode.isBlank()) {
            RoadNodeEntity node = findByCode(parkId, accessNodeCode);
            if (node != null && node.getCoordLng() != null && node.getCoordLat() != null) {
                double distance = GeoPolygonUtils.haversineMeters(point,
                        new GeoPoint(node.getCoordLng(), node.getCoordLat()));
                return new SnapResult(node.getNodeCode(),
                        new GeoPoint(node.getCoordLng(), node.getCoordLat()),
                        distance, distance <= threshold);
            }
        }

        // 2. 在园区内查找最近的道路节点
        if (parkId == null) {
            return null;
        }
        LambdaQueryWrapper<RoadNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoadNodeEntity::getParkId, parkId)
                .eq(RoadNodeEntity::getStatus, "ACTIVE")
                .eq(RoadNodeEntity::getDeleted, 0)
                .isNotNull(RoadNodeEntity::getCoordLng)
                .isNotNull(RoadNodeEntity::getCoordLat);
        List<RoadNodeEntity> nodes = roadNodeMapper.selectList(wrapper);
        if (nodes.isEmpty()) {
            return null;
        }

        RoadNodeEntity nearest = nodes.stream()
                .min(Comparator.comparingDouble(n -> GeoPolygonUtils.haversineMeters(point,
                        new GeoPoint(n.getCoordLng(), n.getCoordLat()))))
                .orElse(null);
        if (nearest == null) {
            return null;
        }
        double distance = GeoPolygonUtils.haversineMeters(point,
                new GeoPoint(nearest.getCoordLng(), nearest.getCoordLat()));
        return new SnapResult(nearest.getNodeCode(),
                new GeoPoint(nearest.getCoordLng(), nearest.getCoordLat()),
                distance, distance <= threshold);
    }

    private RoadNodeEntity findByCode(Long parkId, String code) {
        LambdaQueryWrapper<RoadNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoadNodeEntity::getParkId, parkId)
                .eq(RoadNodeEntity::getNodeCode, code)
                .eq(RoadNodeEntity::getDeleted, 0)
                .last("LIMIT 1");
        return roadNodeMapper.selectOne(wrapper);
    }

    /**
     * 吸附结果。
     *
     * @param nodeCode 道路节点编码
     * @param snappedPoint 吸附后的坐标点
     * @param distanceMeters 吸附距离（米）
     * @param withinThreshold 是否在阈值内
     */
    public record SnapResult(String nodeCode, GeoPoint snappedPoint, double distanceMeters, boolean withinThreshold) {
    }
}
