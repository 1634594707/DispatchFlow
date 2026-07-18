package com.fsd.dispatch.geo;

import com.fsd.dispatch.entity.RouteAuditEntity;
import com.fsd.dispatch.mapper.RouteAuditMapper;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * 路线审计服务（V43 / P0-3.1 / P2-11）。
 *
 * <p>每次路线规划保存审计信息，支持规划路线与实际轨迹对比、偏航分析、重规划追溯。
 */
@Service
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class RouteAuditService {

    private final RouteAuditMapper routeAuditMapper;

    public RouteAuditService(RouteAuditMapper routeAuditMapper) {
        this.routeAuditMapper = routeAuditMapper;
    }

    /**
     * 保存路线规划审计记录。
     *
     * @param routeId 路线ID
     * @param parkId 园区ID
     * @param mapVersionId 地图版本ID
     * @param mapVersionCode 地图版本编码
     * @param routeMode 路线模式
     * @param source 路线来源
     * @param origin 起点
     * @param destination 终点
     * @param plannedPolyline 规划 polyline JSON
     * @param plannedLengthMeters 规划长度
     * @param collisionChecked 是否经过碰撞校验
     * @param crossesBuilding 是否穿越建筑
     * @param crossesRiver 是否穿越河道
     * @param unreachableReason 不可达原因
     * @param status 状态
     * @param taskId 任务ID
     * @param vehicleId 车辆ID
     * @return 保存后的审计记录ID
     */
    public Long saveRouteAudit(String routeId, Long parkId, Long mapVersionId, String mapVersionCode,
                                String routeMode, String source,
                                GeoPoint origin, GeoPoint destination,
                                String plannedPolyline, BigDecimal plannedLengthMeters,
                                boolean collisionChecked, boolean crossesBuilding, boolean crossesRiver,
                                String unreachableReason, String status,
                                Long taskId, Long vehicleId) {
        RouteAuditEntity entity = new RouteAuditEntity();
        entity.setRouteId(routeId);
        entity.setTaskId(taskId);
        entity.setVehicleId(vehicleId);
        entity.setParkId(parkId);
        entity.setMapVersionId(mapVersionId);
        entity.setMapVersionCode(mapVersionCode);
        entity.setRouteMode(routeMode);
        entity.setSource(source);
        entity.setOriginLng(origin != null ? origin.longitude() : null);
        entity.setOriginLat(origin != null ? origin.latitude() : null);
        entity.setDestinationLng(destination != null ? destination.longitude() : null);
        entity.setDestinationLat(destination != null ? destination.latitude() : null);
        entity.setPlannedPolyline(plannedPolyline);
        entity.setPlannedLengthMeters(plannedLengthMeters);
        entity.setCollisionChecked(collisionChecked ? 1 : 0);
        entity.setCrossesBuilding(crossesBuilding ? 1 : 0);
        entity.setCrossesRiver(crossesRiver ? 1 : 0);
        entity.setUnreachableReason(unreachableReason);
        entity.setStatus(status);
        entity.setPlannedAt(LocalDateTime.now());
        entity.setRerouteCount(0);
        entity.setDeleted(0);
        routeAuditMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 更新路线实际轨迹（运行后回填）。
     */
    public void updateActualPolyline(String routeId, String actualPolyline, BigDecimal actualLengthMeters,
                                      BigDecimal deviationMeters, String status) {
        LambdaQueryWrapper<RouteAuditEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouteAuditEntity::getRouteId, routeId)
                .eq(RouteAuditEntity::getDeleted, 0)
                .last("LIMIT 1");
        RouteAuditEntity entity = routeAuditMapper.selectOne(wrapper);
        if (entity == null) {
            return;
        }
        entity.setActualPolyline(actualPolyline);
        entity.setActualLengthMeters(actualLengthMeters);
        entity.setDeviationMeters(deviationMeters);
        entity.setStatus(status);
        if ("COMPLETED".equals(status)) {
            entity.setCompletedAt(LocalDateTime.now());
        }
        routeAuditMapper.updateById(entity);
    }

    /**
     * 增加重规划次数。
     */
    public void incrementRerouteCount(String routeId) {
        LambdaQueryWrapper<RouteAuditEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouteAuditEntity::getRouteId, routeId)
                .eq(RouteAuditEntity::getDeleted, 0)
                .last("LIMIT 1");
        RouteAuditEntity entity = routeAuditMapper.selectOne(wrapper);
        if (entity == null) {
            return;
        }
        entity.setRerouteCount((entity.getRerouteCount() == null ? 0 : entity.getRerouteCount()) + 1);
        routeAuditMapper.updateById(entity);
    }

    /**
     * 查询某车辆的路线审计记录。
     */
    public List<RouteAuditEntity> listByVehicle(Long vehicleId, int limit) {
        LambdaQueryWrapper<RouteAuditEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouteAuditEntity::getVehicleId, vehicleId)
                .eq(RouteAuditEntity::getDeleted, 0)
                .orderByDesc(RouteAuditEntity::getPlannedAt)
                .last("LIMIT " + Math.max(1, limit));
        return routeAuditMapper.selectList(wrapper);
    }
}
