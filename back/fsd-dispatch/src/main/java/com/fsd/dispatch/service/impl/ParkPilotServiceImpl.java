package com.fsd.dispatch.service.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkRoadNodeResponse;
import com.fsd.dispatch.vo.ParkRoadSegmentResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ParkPilotServiceImpl implements ParkPilotService {

    private final ParkPilotProperties parkPilotProperties;
    private final ParkPilotSimulationService parkPilotSimulationService;
    private final VehicleMapper vehicleMapper;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;

    public ParkPilotServiceImpl(ParkPilotProperties parkPilotProperties,
                                ParkPilotSimulationService parkPilotSimulationService,
                                VehicleMapper vehicleMapper,
                                ParkRoutePlannerService parkRoutePlannerService,
                                OrderMapper orderMapper,
                                DispatchTaskMapper dispatchTaskMapper) {
        this.parkPilotProperties = parkPilotProperties;
        this.parkPilotSimulationService = parkPilotSimulationService;
        this.vehicleMapper = vehicleMapper;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
    }

    @Override
    public ParkLayoutResponse getLayout() {
        return ParkLayoutResponse.builder()
                .enabled(parkPilotProperties.isEnabled())
                .width(parkPilotProperties.getWidth())
                .height(parkPilotProperties.getHeight())
                .minZoom(parkPilotProperties.getMinZoom())
                .maxZoom(parkPilotProperties.getMaxZoom())
                .vehicleSpeedPxPerSecond(parkPilotProperties.getVehicleSpeedPxPerSecond())
                .xFieldAlias(parkPilotProperties.getXFieldAlias())
                .yFieldAlias(parkPilotProperties.getYFieldAlias())
                .stations(listStations())
                .parkingSpots(parkPilotProperties.getParkingSpots().stream()
                        .map(point -> ParkPointResponse.builder()
                                .code(point.getCode())
                                .x(point.getX())
                                .y(point.getY())
                                .build())
                        .toList())
                .roadNodes(parkPilotProperties.getRoadNodes().stream()
                        .map(node -> ParkRoadNodeResponse.builder()
                                .code(node.getCode())
                                .x(node.getX())
                                .y(node.getY())
                                .build())
                        .toList())
                .roadSegments(parkPilotProperties.getRoadSegments().stream()
                        .map(segment -> ParkRoadSegmentResponse.builder()
                                .from(segment.getFrom())
                                .to(segment.getTo())
                                .build())
                        .toList())
                .build();
    }

    @Override
    public List<ParkStationResponse> listStations() {
        return parkPilotProperties.getStations().stream()
                .map(this::toStationResponse)
                .toList();
    }

    @Override
    public ParkStationResponse getStation(Long stationId) {
        return parkPilotProperties.getStations().stream()
                .filter(station -> stationId.equals(station.getId()))
                .findFirst()
                .map(this::toStationResponse)
                .orElseThrow(() -> new BusinessException("PARK_STATION_NOT_FOUND", "Park station not found"));
    }

    @Override
    public VehicleEntity selectNearestVehicle(List<VehicleEntity> candidates, Long stationId) {
        ParkStationResponse station = getStation(stationId);
        return candidates.stream()
                .min(Comparator.comparingDouble(vehicle -> calculateRouteDistance(vehicle, station)))
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_ASSIGNABLE", "Vehicle is not assignable"));
    }

    @Override
    public List<ParkVehicleSnapshotResponse> listVehicleSnapshots() {
        parkPilotSimulationService.initializeVehiclesIfNeeded();
        List<VehicleEntity> vehicles = vehicleMapper.selectList(null).stream()
                .filter(vehicle -> vehicle.getVehicleCode() != null && vehicle.getVehicleCode().startsWith("PARK-"))
                .toList();
        return parkPilotSimulationService.buildSnapshots(vehicles);
    }

    @Override
    public List<ParkOrderSnapshotResponse> listOrderSnapshots() {
        Map<Long, DispatchTaskEntity> taskById = dispatchTaskMapper.selectList(null).stream()
                .filter(task -> task.getDeleted() == null || task.getDeleted() == 0)
                .collect(Collectors.toMap(DispatchTaskEntity::getId, Function.identity(), (left, right) -> left));

        Map<Long, ParkVehicleSnapshotResponse> vehicleByTaskId = listVehicleSnapshots().stream()
                .filter(vehicle -> vehicle.getCurrentTaskId() != null)
                .collect(Collectors.toMap(ParkVehicleSnapshotResponse::getCurrentTaskId, Function.identity(), (left, right) -> left));

        Set<Long> stationIds = parkPilotProperties.getStations().stream()
                .map(ParkPilotProperties.StationConfig::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return orderMapper.selectList(null).stream()
                .filter(order -> order.getDeleted() == null || order.getDeleted() == 0)
                .filter(order -> stationIds.contains(order.getPickupPointId()) && stationIds.contains(order.getDropoffPointId()))
                .sorted(Comparator.comparing(OrderEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(order -> toOrderSnapshot(order, taskById.get(order.getDispatchTaskId()), vehicleByTaskId))
                .toList();
    }

    private double calculateRouteDistance(VehicleEntity vehicle, ParkStationResponse station) {
        BigDecimal currentX = vehicle.getCurrentLongitude();
        BigDecimal currentY = vehicle.getCurrentLatitude();
        if (currentX == null || currentY == null) {
            return Double.MAX_VALUE;
        }
        try {
            return calculatePathLength(parkRoutePlannerService.buildRoute(currentX, currentY, station.getX(), station.getY()));
        } catch (BusinessException ex) {
            double dx = currentX.doubleValue() - station.getX().doubleValue();
            double dy = currentY.doubleValue() - station.getY().doubleValue();
            return Math.hypot(dx, dy);
        }
    }

    private ParkStationResponse toStationResponse(ParkPilotProperties.StationConfig station) {
        return ParkStationResponse.builder()
                .stationId(station.getId())
                .stationCode(station.getCode())
                .stationName(station.getName())
                .x(station.getX())
                .y(station.getY())
                .area(station.getArea())
                .build();
    }

    private double calculatePathLength(List<ParkPointResponse> route) {
        if (route == null || route.size() < 2) {
            return 0D;
        }
        double total = 0D;
        ParkPointResponse previous = route.get(0);
        for (int i = 1; i < route.size(); i++) {
            ParkPointResponse current = route.get(i);
            total += Math.hypot(
                    current.getX().doubleValue() - previous.getX().doubleValue(),
                    current.getY().doubleValue() - previous.getY().doubleValue());
            previous = current;
        }
        return total;
    }

    private ParkOrderSnapshotResponse toOrderSnapshot(OrderEntity order,
                                                      DispatchTaskEntity task,
                                                      Map<Long, ParkVehicleSnapshotResponse> vehicleByTaskId) {
        ParkVehicleSnapshotResponse vehicleSnapshot = task == null ? null : vehicleByTaskId.get(task.getId());
        return ParkOrderSnapshotResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .orderStatus(order.getStatus())
                .taskId(task == null ? null : task.getId())
                .taskNo(task == null ? null : task.getTaskNo())
                .taskStatus(task == null ? null : task.getStatus())
                .vehicleId(task == null ? null : task.getVehicleId())
                .vehicleCode(vehicleSnapshot == null ? null : vehicleSnapshot.getVehicleCode())
                .vehicleName(vehicleSnapshot == null ? null : vehicleSnapshot.getVehicleName())
                .runtimeStage(resolveRuntimeStage(order, task, vehicleSnapshot))
                .pickupStation(getStation(order.getPickupPointId()))
                .dropoffStation(getStation(order.getDropoffPointId()))
                .assignTime(task == null ? null : task.getAssignTime())
                .startTime(task == null ? null : task.getStartTime())
                .finishTime(task == null ? null : task.getFinishTime())
                .updatedAt(resolveUpdatedAt(order, task))
                .build();
    }

    private String resolveRuntimeStage(OrderEntity order,
                                       DispatchTaskEntity task,
                                       ParkVehicleSnapshotResponse vehicleSnapshot) {
        if ("COMPLETED".equals(order.getStatus())) {
            return "COMPLETED";
        }
        if ("FAILED".equals(order.getStatus()) || (task != null && "FAILED".equals(task.getStatus()))) {
            return "FAILED";
        }
        if (vehicleSnapshot != null && vehicleSnapshot.getRuntimeStage() != null) {
            return switch (vehicleSnapshot.getRuntimeStage()) {
                case "TO_PICKUP" -> "HEADING_TO_PICKUP";
                case "LOADING" -> "LOADING";
                case "TO_DROPOFF" -> "HEADING_TO_DROPOFF";
                case "UNLOADING" -> "UNLOADING";
                default -> vehicleSnapshot.getRuntimeStage();
            };
        }
        if (task == null) {
            return "PENDING_ASSIGNMENT";
        }
        return switch (task.getStatus()) {
            case "PENDING", "ASSIGNING" -> "PENDING_ASSIGNMENT";
            case "ASSIGNED" -> "HEADING_TO_PICKUP";
            case "EXECUTING" -> "HEADING_TO_DROPOFF";
            case "SUCCESS" -> "COMPLETED";
            case "FAILED", "MANUAL_PENDING", "CANCELLED" -> task.getStatus();
            default -> task.getStatus();
        };
    }

    private java.time.LocalDateTime resolveUpdatedAt(OrderEntity order, DispatchTaskEntity task) {
        List<java.time.LocalDateTime> candidates = new ArrayList<>();
        if (order != null && order.getUpdatedAt() != null) {
            candidates.add(order.getUpdatedAt());
        }
        if (task != null && task.getUpdatedAt() != null) {
            candidates.add(task.getUpdatedAt());
        }
        return candidates.stream().filter(Objects::nonNull).max(java.time.LocalDateTime::compareTo).orElse(null);
    }
}
