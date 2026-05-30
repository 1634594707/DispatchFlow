package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminTrafficPauseZoneRequest;
import com.fsd.admin.service.TrafficAdminService;
import com.fsd.admin.vo.AdminTrafficPauseZoneResponse;
import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import com.fsd.admin.vo.AdminTrafficSummaryResponse;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.TrafficZoneControlService;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TrafficAdminServiceImpl implements TrafficAdminService {

    private static final double NEAR_RADIUS = 80D;
    private static final String ROAD_DISABLED = "DISABLED";

    private final RoadSegmentMapper roadSegmentMapper;
    private final RoadNodeMapper roadNodeMapper;
    private final ParkPilotService parkPilotService;
    private final TrafficZoneControlService trafficZoneControlService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final OrderMapper orderMapper;
    private final ParkStationService parkStationService;

    public TrafficAdminServiceImpl(RoadSegmentMapper roadSegmentMapper,
                                   RoadNodeMapper roadNodeMapper,
                                   ParkPilotService parkPilotService,
                                   TrafficZoneControlService trafficZoneControlService,
                                   DispatchTaskMapper dispatchTaskMapper,
                                   OrderMapper orderMapper,
                                   ParkStationService parkStationService) {
        this.roadSegmentMapper = roadSegmentMapper;
        this.roadNodeMapper = roadNodeMapper;
        this.parkPilotService = parkPilotService;
        this.trafficZoneControlService = trafficZoneControlService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.orderMapper = orderMapper;
        this.parkStationService = parkStationService;
    }

    @Override
    public List<AdminTrafficSegmentResponse> getTrafficOverview(Long parkId) {
        Long resolvedParkId = resolveParkId(parkId);
        List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(new LambdaQueryWrapper<RoadSegmentEntity>()
                .eq(RoadSegmentEntity::getParkId, resolvedParkId)
                .eq(RoadSegmentEntity::getDeleted, 0));
        Map<String, RoadNodeEntity> nodes = loadNodes(resolvedParkId);
        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots();
        return segments.stream()
                .map(segment -> buildSegmentResponse(segment, nodes, vehicles, resolvedParkId))
                .toList();
    }

    @Override
    public void refreshCongestion(Long parkId) {
        Long resolvedParkId = resolveParkId(parkId);
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

    @Override
    public AdminTrafficSummaryResponse getSummary(Long parkId) {
        Long resolvedParkId = resolveParkId(parkId);
        List<AdminTrafficSegmentResponse> segments = getTrafficOverview(resolvedParkId);
        int maxLevel = segments.stream()
                .mapToInt(s -> s.getCongestionLevel() == null ? 0 : s.getCongestionLevel())
                .max()
                .orElse(0);
        int highCount = (int) segments.stream()
                .filter(s -> s.getCongestionLevel() != null && s.getCongestionLevel() >= 2)
                .count();
        int disabled = (int) segments.stream()
                .filter(s -> ROAD_DISABLED.equalsIgnoreCase(s.getStatus()))
                .count();
        return AdminTrafficSummaryResponse.builder()
                .parkId(resolvedParkId)
                .maxCongestionLevel(maxLevel)
                .highCongestionSegmentCount(highCount)
                .pausedZoneCount(trafficZoneControlService.listPauseZones(resolvedParkId).size())
                .disabledSegmentCount(disabled)
                .build();
    }

    @Override
    public int countAffectedTasksForSegment(Long segmentId) {
        RoadSegmentEntity segment = requireSegment(segmentId);
        Map<String, RoadNodeEntity> nodes = loadNodes(segment.getParkId());
        return countAffectedTasksNearSegment(nodes.get(segment.getFromNodeCode()), nodes.get(segment.getToNodeCode()), segment.getParkId());
    }

    @Override
    public AdminTrafficSegmentResponse disableSegment(Long segmentId) {
        RoadSegmentEntity segment = requireSegment(segmentId);
        segment.setStatus(ROAD_DISABLED.equalsIgnoreCase(segment.getStatus()) ? "ACTIVE" : ROAD_DISABLED);
        roadSegmentMapper.updateById(segment);
        return buildSegmentResponse(segment, loadNodes(segment.getParkId()), parkPilotService.listVehicleSnapshots(), segment.getParkId());
    }

    @Override
    public AdminTrafficSegmentResponse downgradeCongestion(Long segmentId) {
        RoadSegmentEntity segment = requireSegment(segmentId);
        int current = segment.getCongestionLevel() == null ? 0 : segment.getCongestionLevel();
        segment.setCongestionLevel(Math.max(0, current - 1));
        roadSegmentMapper.updateById(segment);
        return buildSegmentResponse(segment, loadNodes(segment.getParkId()), parkPilotService.listVehicleSnapshots(), segment.getParkId());
    }

    @Override
    public List<AdminTrafficPauseZoneResponse> listPauseZones(Long parkId) {
        return trafficZoneControlService.listPauseZones(resolveParkId(parkId)).stream()
                .map(zone -> AdminTrafficPauseZoneResponse.builder()
                        .minX(zone.minX())
                        .minY(zone.minY())
                        .maxX(zone.maxX())
                        .maxY(zone.maxY())
                        .label(zone.label())
                        .build())
                .toList();
    }

    @Override
    public AdminTrafficPauseZoneResponse addPauseZone(AdminTrafficPauseZoneRequest request) {
        var zone = trafficZoneControlService.addPauseZone(
                request.getParkId(),
                request.getMinX(),
                request.getMinY(),
                request.getMaxX(),
                request.getMaxY(),
                request.getLabel());
        return AdminTrafficPauseZoneResponse.builder()
                .minX(zone.minX())
                .minY(zone.minY())
                .maxX(zone.maxX())
                .maxY(zone.maxY())
                .label(zone.label())
                .build();
    }

    @Override
    public void clearPauseZones(Long parkId) {
        trafficZoneControlService.clearPauseZones(resolveParkId(parkId));
    }

    private AdminTrafficSegmentResponse buildSegmentResponse(RoadSegmentEntity segment,
                                                             Map<String, RoadNodeEntity> nodes,
                                                             List<ParkVehicleSnapshotResponse> vehicles,
                                                             Long parkId) {
        RoadNodeEntity from = nodes.get(segment.getFromNodeCode());
        RoadNodeEntity to = nodes.get(segment.getToNodeCode());
        return AdminTrafficSegmentResponse.builder()
                .segmentId(segment.getId())
                .fromNodeCode(segment.getFromNodeCode())
                .toNodeCode(segment.getToNodeCode())
                .status(segment.getStatus())
                .speedLimitKmh(segment.getSpeedLimitKmh())
                .congestionLevel(segment.getCongestionLevel())
                .nearbyVehicleCount(countVehiclesNearSegment(from, to, vehicles))
                .affectedTaskCount(countAffectedTasksNearSegment(from, to, parkId))
                .build();
    }

    private RoadSegmentEntity requireSegment(Long segmentId) {
        RoadSegmentEntity segment = roadSegmentMapper.selectById(segmentId);
        if (segment == null || (segment.getDeleted() != null && segment.getDeleted() != 0)) {
            throw new BusinessException("SEGMENT_NOT_FOUND", "路段不存在");
        }
        return segment;
    }

    private Map<String, RoadNodeEntity> loadNodes(Long parkId) {
        return roadNodeMapper.selectList(new LambdaQueryWrapper<RoadNodeEntity>()
                        .eq(RoadNodeEntity::getParkId, parkId)
                        .eq(RoadNodeEntity::getDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.toMap(RoadNodeEntity::getNodeCode, n -> n, (a, b) -> a));
    }

    private int countAffectedTasksNearSegment(RoadNodeEntity from, RoadNodeEntity to, Long parkId) {
        if (from == null || to == null) {
            return 0;
        }
        double midX = (from.getCoordX().doubleValue() + to.getCoordX().doubleValue()) / 2D;
        double midY = (from.getCoordY().doubleValue() + to.getCoordY().doubleValue()) / 2D;
        Set<String> activeStatuses = Set.of(
                DispatchTaskStatus.PENDING.name(),
                DispatchTaskStatus.MANUAL_PENDING.name(),
                DispatchTaskStatus.ASSIGNED.name(),
                DispatchTaskStatus.EXECUTING.name());
        List<DispatchTaskEntity> tasks = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .in(DispatchTaskEntity::getStatus, activeStatuses));
        int count = 0;
        for (DispatchTaskEntity task : tasks) {
            if (task.getOrderId() == null) {
                continue;
            }
            OrderEntity order = orderMapper.selectById(task.getOrderId());
            if (order == null || (order.getParkId() != null && !parkId.equals(order.getParkId()))) {
                continue;
            }
            if (isStationNear(order.getPickupPointId(), midX, midY)
                    || isStationNear(order.getDropoffPointId(), midX, midY)) {
                count++;
            }
        }
        return count;
    }

    private boolean isStationNear(Long stationId, double midX, double midY) {
        if (stationId == null) {
            return false;
        }
        try {
            ParkStationResponse station = parkStationService.requireStation(stationId);
            if (station.getX() == null || station.getY() == null) {
                return false;
            }
            return Math.hypot(station.getX().doubleValue() - midX, station.getY().doubleValue() - midY) <= NEAR_RADIUS * 2;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private Long resolveParkId(Long parkId) {
        return parkId != null ? parkId : parkStationService.requireDefaultPark().getId();
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
