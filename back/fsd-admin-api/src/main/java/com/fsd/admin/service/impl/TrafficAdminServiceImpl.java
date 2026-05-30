package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.TrafficAdminService;
import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TrafficAdminServiceImpl implements TrafficAdminService {

    private static final double NEAR_RADIUS = 80D;

    private final RoadSegmentMapper roadSegmentMapper;
    private final RoadNodeMapper roadNodeMapper;
    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final ParkPilotService parkPilotService;

    public TrafficAdminServiceImpl(RoadSegmentMapper roadSegmentMapper,
                                   RoadNodeMapper roadNodeMapper,
                                   VehicleMapper vehicleMapper,
                                   FleetRuntimeService fleetRuntimeService,
                                   ParkPilotService parkPilotService) {
        this.roadSegmentMapper = roadSegmentMapper;
        this.roadNodeMapper = roadNodeMapper;
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.parkPilotService = parkPilotService;
    }

    @Override
    public List<AdminTrafficSegmentResponse> getTrafficOverview(Long parkId) {
        Long resolvedParkId = parkId != null ? parkId : 1L;
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new LambdaQueryWrapper<RoadSegmentEntity>()
                .eq(RoadSegmentEntity::getParkId, resolvedParkId)
                .eq(RoadSegmentEntity::getDeleted, 0));
        Map<String, RoadNodeEntity> nodes = roadNodeMapper.selectList(new LambdaQueryWrapper<RoadNodeEntity>()
                        .eq(RoadNodeEntity::getParkId, resolvedParkId)
                        .eq(RoadNodeEntity::getDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.toMap(RoadNodeEntity::getNodeCode, n -> n, (a, b) -> a));

        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots();
        return segments.stream()
                .map(segment -> {
                    RoadNodeEntity from = nodes.get(segment.getFromNodeCode());
                    RoadNodeEntity to = nodes.get(segment.getToNodeCode());
                    int count = countVehiclesNearSegment(from, to, vehicles);
                    return AdminTrafficSegmentResponse.builder()
                            .segmentId(segment.getId())
                            .fromNodeCode(segment.getFromNodeCode())
                            .toNodeCode(segment.getToNodeCode())
                            .status(segment.getStatus())
                            .speedLimitKmh(segment.getSpeedLimitKmh())
                            .congestionLevel(segment.getCongestionLevel())
                            .nearbyVehicleCount(count)
                            .build();
                })
                .toList();
    }

    @Override
    public void refreshCongestion(Long parkId) {
        Long resolvedParkId = parkId != null ? parkId : 1L;
        for (AdminTrafficSegmentResponse item : getTrafficOverview(resolvedParkId)) {
            RoadSegmentEntity segment = roadSegmentMapper.selectById(item.getSegmentId());
            if (segment == null) {
                continue;
            }
            int level = item.getNearbyVehicleCount() >= 3 ? 3
                    : item.getNearbyVehicleCount() >= 2 ? 2
                    : item.getNearbyVehicleCount() >= 1 ? 1 : 0;
            segment.setCongestionLevel(level);
            roadSegmentMapper.updateById(segment);
        }
    }

    private int countVehiclesNearSegment(RoadNodeEntity from, RoadNodeEntity to,
                                         List<ParkVehicleSnapshotResponse> vehicles) {
        if (from == null || to == null) {
            return 0;
        }
        double midX = (from.getCoordX().doubleValue() + to.getCoordX().doubleValue()) / 2D;
        double midY = (from.getCoordY().doubleValue() + to.getCoordY().doubleValue()) / 2D;
        int count = 0;
        for (ParkVehicleSnapshotResponse vehicle : vehicles) {
            if (vehicle.getX() == null || vehicle.getY() == null) {
                continue;
            }
            double dist = Math.hypot(vehicle.getX().doubleValue() - midX, vehicle.getY().doubleValue() - midY);
            if (dist <= NEAR_RADIUS) {
                count++;
            }
        }
        return count;
    }
}
