package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.fleet.PilotFleetSupport;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.fleet.service.FleetSnapshotAssembler;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkGeofenceResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOverviewResponse;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkRoadNodeResponse;
import com.fsd.dispatch.vo.ParkRoadSegmentResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkTrackResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.order.entity.OrderEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ParkPilotServiceImpl implements ParkPilotService {

    private final ParkPilotProperties parkPilotProperties;
    private final ParkStationService parkStationService;
    private final ParkPilotSimulationService parkPilotSimulationService;
    private final VehicleMapper vehicleMapper;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final FleetSnapshotAssembler fleetSnapshotAssembler;
    private final ParkGeofenceService parkGeofenceService;
    private final com.fsd.dispatch.geo.VehiclePositionResolver vehiclePositionResolver;

    public ParkPilotServiceImpl(ParkPilotProperties parkPilotProperties,
                                ParkStationService parkStationService,
                                ParkPilotSimulationService parkPilotSimulationService,
                                VehicleMapper vehicleMapper,
                                ParkRoutePlannerService parkRoutePlannerService,
                                OrderMapper orderMapper,
                                DispatchTaskMapper dispatchTaskMapper,
                                FleetRuntimeService fleetRuntimeService,
                                FleetSnapshotAssembler fleetSnapshotAssembler,
                                ParkGeofenceService parkGeofenceService,
                                com.fsd.dispatch.geo.VehiclePositionResolver vehiclePositionResolver) {
        this.parkPilotProperties = parkPilotProperties;
        this.parkStationService = parkStationService;
        this.parkPilotSimulationService = parkPilotSimulationService;
        this.vehicleMapper = vehicleMapper;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetSnapshotAssembler = fleetSnapshotAssembler;
        this.parkGeofenceService = parkGeofenceService;
        this.vehiclePositionResolver = vehiclePositionResolver;
    }

    @Override
    public List<ParkResponse> listParks() {
        return parkStationService.listActiveParks();
    }

    @Override
    public ParkLayoutResponse getLayout() {
        return getLayout(parkStationService.requireDefaultPark().getId());
    }

    @Override
    public ParkLayoutResponse getLayout(Long parkId) {
        if (parkId == null) {
            return getLayout();
        }
        ParkEntity park = parkStationService.requirePark(parkId);
        return ParkLayoutResponse.builder()
                .enabled(parkPilotProperties.isEnabled())
                .parkId(park.getId())
                .parkCode(park.getParkCode())
                .parkName(park.getParkName())
                .width(resolveMapDimension(park.getMapWidth(), parkPilotProperties.getWidth()))
                .height(resolveMapDimension(park.getMapHeight(), parkPilotProperties.getHeight()))
                .minZoom(resolveMapDimension(park.getMinZoom(), parkPilotProperties.getMinZoom()))
                .maxZoom(resolveMapDimension(park.getMaxZoom(), parkPilotProperties.getMaxZoom()))
                .vehicleSpeedPxPerSecond(park.getVehicleSpeedPxPerSecond() != null
                        ? park.getVehicleSpeedPxPerSecond()
                        : parkPilotProperties.getVehicleSpeedPxPerSecond())
                .xFieldAlias(parkPilotProperties.getXFieldAlias())
                .yFieldAlias(parkPilotProperties.getYFieldAlias())
                .centerLng(resolveCenterLng(park))
                .centerLat(resolveCenterLat(park))
                .mapProvider(resolveMapProvider(park))
                .stations(listStations(parkId))
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
        return listStations(parkStationService.requireDefaultPark().getId());
    }

    @Override
    public List<ParkStationResponse> listStations(Long parkId) {
        return parkStationService.listStations(parkId);
    }

    @Override
    public ParkStationResponse getStation(Long stationId) {
        return parkStationService.requireStation(stationId);
    }

    private Integer resolveMapDimension(Integer parkValue, Integer fallback) {
        return parkValue != null ? parkValue : fallback;
    }

    private BigDecimal resolveCenterLng(ParkEntity park) {
        if (park.getCenterLng() != null) {
            return park.getCenterLng();
        }
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        return geo != null && geo.isEnabled() ? geo.getAnchorLng() : null;
    }

    private BigDecimal resolveCenterLat(ParkEntity park) {
        if (park.getCenterLat() != null) {
            return park.getCenterLat();
        }
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        return geo != null && geo.isEnabled() ? geo.getAnchorLat() : null;
    }

    private String resolveMapProvider(ParkEntity park) {
        if (park.getMapProvider() != null && !park.getMapProvider().isBlank()) {
            return park.getMapProvider();
        }
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        return geo != null && geo.isEnabled() ? "AMAP" : null;
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
        return listVehicleSnapshots(null);
    }

    @Override
    public List<ParkVehicleSnapshotResponse> listVehicleSnapshots(Long parkId) {
        parkPilotSimulationService.initializeVehiclesIfNeeded();
        List<VehicleEntity> vehicles = vehicleMapper.selectList(null).stream()
                .filter(vehicle -> vehicle.getDeleted() == null || vehicle.getDeleted() == 0)
                .filter(vehicle -> parkId == null || parkId.equals(vehicle.getParkId()))
                .filter(vehicle -> isMonitorVehicle(vehicle.getVehicleCode()))
                .toList();
        List<VehicleEntity> simVehicles = vehicles.stream().filter(this::isSimulationVehicle).toList();
        List<VehicleEntity> realVehicles = vehicles.stream().filter(this::isRealVehicle).toList();

        List<ParkVehicleSnapshotResponse> snapshots = new ArrayList<>();
        if (!simVehicles.isEmpty()) {
            snapshots.addAll(parkPilotSimulationService.buildSnapshots(simVehicles));
        }
        for (VehicleEntity realVehicle : realVehicles) {
            FleetRuntime runtime = fleetRuntimeService.get(realVehicle.getId()).orElse(null);
            snapshots.add(fleetSnapshotAssembler.assemble(realVehicle, runtime));
        }
        return snapshots.stream()
                .sorted(Comparator.comparing(ParkVehicleSnapshotResponse::getVehicleCode))
                .toList();
    }

    private boolean isMonitorVehicle(String vehicleCode) {
        return vehicleCode != null
                && (PilotFleetSupport.isPilotSimVehicleCode(vehicleCode)
                || vehicleCode.startsWith("REAL-")
                || vehicleCode.startsWith("VDA5050-"));
    }

    private boolean isSimulationVehicle(VehicleEntity vehicle) {
        return VehicleLinkMode.isSimulated(vehicle.getLinkMode());
    }

    private boolean isRealVehicle(VehicleEntity vehicle) {
        return VehicleLinkMode.issuesExternalCommands(vehicle.getLinkMode());
    }

    @Override
    public List<ParkOrderSnapshotResponse> listOrderSnapshots() {
        return listOrderSnapshots(null);
    }

    @Override
    public List<ParkOrderSnapshotResponse> listOrderSnapshots(Long parkId) {
        Map<Long, DispatchTaskEntity> taskById = dispatchTaskMapper.selectList(null).stream()
                .filter(task -> task.getDeleted() == null || task.getDeleted() == 0)
                .collect(Collectors.toMap(DispatchTaskEntity::getId, Function.identity(), (left, right) -> left));

        Map<Long, ParkVehicleSnapshotResponse> vehicleByTaskId = listVehicleSnapshots().stream()
                .filter(vehicle -> vehicle.getCurrentTaskId() != null)
                .collect(Collectors.toMap(ParkVehicleSnapshotResponse::getCurrentTaskId, Function.identity(), (left, right) -> left));

        ParkEntity defaultPark = parkId == null
                ? parkStationService.requireDefaultPark()
                : parkStationService.requirePark(parkId);
        Set<Long> stationIds = parkStationService.listStations(defaultPark.getId()).stream()
                .map(ParkStationResponse::getStationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return orderMapper.selectList(null).stream()
                .filter(order -> order.getDeleted() == null || order.getDeleted() == 0)
                .filter(order -> matchesParkOrder(order, defaultPark.getId(), stationIds))
                .sorted(Comparator.comparing(OrderEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(order -> toOrderSnapshot(order, taskById.get(order.getDispatchTaskId()), vehicleByTaskId))
                .toList();
    }

    /** "这一单不用再追踪了"的终态集合：自动挑单、activeCount 与精简行的阶段判定共用这一份定义。 */
    private static final List<String> TERMINAL_ORDER_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");

    @Override
    public ParkTrackResponse buildTrackSnapshot(Long parkId, Long orderId, int recentLimit) {
        ParkEntity park = parkId == null ? parkStationService.requireDefaultPark() : parkStationService.requirePark(parkId);
        // 一次读回园区站点表：既是"这一单属不属于本园区"的判据集合，也是精简行的标签来源。
        // 精简行原来按行 requireStation（8 条单 = 16 次回查），而这张表本来就已经读过了。
        Map<Long, ParkStationResponse> parkStations = parkStationService.listStations(park.getId()).stream()
                .collect(Collectors.toMap(ParkStationResponse::getStationId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Set<Long> parkStationIds = parkStations.keySet();
        int limit = Math.min(Math.max(recentLimit, 1), 20);

        // 最近列表：按主键倒序取一小撮候选，再按园区归属过滤。
        // 为什么不像 listOrderSnapshots 那样 selectList(null) 再在内存里排序：那条在读侧是 O(全表)，
        // 手机页每 1.5 s 打一次 —— 本机预压实测"积压 968 单时 tracking_poll p95 从 155 ms 涨到 1.07 s"
        // 就是它，体积也只是次要的那一半。
        // 为什么按 id 不按 updated_at：t_order 实测没有 updated_at 索引（只有 idx_park_id 与
        // idx_status_created_at），按 updated_at 排会全表 filesort，等于把省下的开销换个地方付；
        // id 单调递增，"最新的一单"与产品语义一致。
        // 候选取 limit×3：吸收候选里混着别的园区/站点已删的单被过滤掉的情况。
        List<OrderEntity> candidates = orderMapper.selectList(Wrappers.<OrderEntity>lambdaQuery()
                        .eq(OrderEntity::getDeleted, 0)
                        .orderByDesc(OrderEntity::getId)
                        .last("LIMIT " + (limit * 3)))
                .stream()
                .filter(order -> matchesParkOrder(order, park.getId(), parkStationIds))
                .limit(limit)
                .toList();

        Map<Long, DispatchTaskEntity> taskById = loadTasksFor(candidates);

        OrderEntity tracked;
        if (orderId != null) {
            OrderEntity byId = orderMapper.selectById(orderId);
            if (byId == null || (byId.getDeleted() != null && byId.getDeleted() != 0)) {
                throw new BusinessException("ORDER_NOT_FOUND", "订单不存在或已删除");
            }
            if (!matchesParkOrder(byId, park.getId(), parkStationIds)) {
                throw new BusinessException("PARK_SCOPE_DENIED", "记录不属于当前园区");
            }
            tracked = byId;
        } else {
            tracked = candidates.stream()
                    .filter(order -> !isTerminalOrder(order.getStatus()))
                    .findFirst()
                    .orElse(candidates.isEmpty() ? null : candidates.get(0));
        }

        ParkOrderSnapshotResponse orderSnapshot = null;
        ParkVehicleSnapshotResponse vehicleSnapshot = null;
        if (tracked != null) {
            DispatchTaskEntity task = tracked.getDispatchTaskId() == null
                    ? null
                    : dispatchTaskMapper.selectById(tracked.getDispatchTaskId());
            vehicleSnapshot = snapshotOfTrackedVehicle(task);
            Map<Long, ParkVehicleSnapshotResponse> vehicleByTaskId = task == null || vehicleSnapshot == null
                    ? Map.of()
                    : Map.of(task.getId(), vehicleSnapshot);
            orderSnapshot = toOrderSnapshot(tracked, task, vehicleByTaskId);
        }

        return ParkTrackResponse.builder()
                .order(orderSnapshot)
                .vehicle(vehicleSnapshot)
                .activeCount(orderMapper.selectCount(Wrappers.<OrderEntity>lambdaQuery()
                        .eq(OrderEntity::getParkId, park.getId())
                        .eq(OrderEntity::getDeleted, 0)
                        .notIn(OrderEntity::getStatus, TERMINAL_ORDER_STATUSES)))
                .recentOrders(candidates.stream()
                        .map(order -> toRecentOrder(order, taskById.get(order.getDispatchTaskId()), parkStations))
                        .toList())
                .build();
    }

    /** 一次批量取候选订单对应的任务，避免按行回查（候选最多 20 条）。 */
    private Map<Long, DispatchTaskEntity> loadTasksFor(List<OrderEntity> orders) {
        Set<Long> taskIds = orders.stream()
                .map(OrderEntity::getDispatchTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return dispatchTaskMapper.selectBatchIds(taskIds).stream()
                .filter(task -> task.getDeleted() == null || task.getDeleted() == 0)
                .collect(Collectors.toMap(DispatchTaskEntity::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 只构建**这一辆**车的快照。
     *
     * <p>不复用 listVehicleSnapshots(parkId)：那函数除了把 35 台车连同三条折线一起装配出来，
     * 还会先调 initializeVehiclesIfNeeded()，并对每台车 publishTelemetry —— 一个被 1.5 s 轮询一次的
     * 只读接口不该每次重铺车队并写遥测。这里 SIM 行走 buildSnapshots(List.of(vehicle))（与整园读法
     * 逐字一致，只是只有一台），真行走 fleetSnapshotAssembler。
     */
    private ParkVehicleSnapshotResponse snapshotOfTrackedVehicle(DispatchTaskEntity task) {
        if (task == null || task.getVehicleId() == null) {
            return null;
        }
        VehicleEntity vehicle = vehicleMapper.selectById(task.getVehicleId());
        if (vehicle == null
                || (vehicle.getDeleted() != null && vehicle.getDeleted() != 0)
                || !isMonitorVehicle(vehicle.getVehicleCode())) {
            return null;
        }
        if (isSimulationVehicle(vehicle)) {
            List<ParkVehicleSnapshotResponse> one = parkPilotSimulationService.buildSnapshots(List.of(vehicle));
            return one.isEmpty() ? null : one.get(0);
        }
        FleetRuntime runtime = fleetRuntimeService.get(vehicle.getId()).orElse(null);
        return fleetSnapshotAssembler.assemble(vehicle, runtime);
    }

    /**
     * 切换芯片用的精简行。runtimeStage 刻意**不**带车队实时阶段（那是 resolveRuntimeStage 收到
     * vehicleSnapshot 时才有的口径）：这里只需要"这单还在不在跑"，为此把 N 台车快照读回来
     * 就把这个接口的意义抵消了。精确阶段由 order/vehicle 两个完整快照提供。
     */
    private ParkTrackResponse.RecentOrder toRecentOrder(OrderEntity order, DispatchTaskEntity task,
                                                        Map<Long, ParkStationResponse> parkStations) {
        // 站点在这份响应里只是标签来源：拿不到就留 null，绝不让一次轮询因为一个孤儿站点引用而 500
        ParkStationResponse pickup = parkStations.get(order.getPickupPointId());
        ParkStationResponse dropoff = parkStations.get(order.getDropoffPointId());
        return ParkTrackResponse.RecentOrder.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .orderStatus(order.getStatus())
                .runtimeStage(resolveRuntimeStage(order, task, null))
                .vehicleId(task == null ? null : task.getVehicleId())
                .pickupStationCode(pickup == null ? null : pickup.getStationCode())
                .pickupStationArea(pickup == null ? null : pickup.getArea())
                .dropoffStationCode(dropoff == null ? null : dropoff.getStationCode())
                .dropoffStationArea(dropoff == null ? null : dropoff.getArea())
                .build();
    }

    private boolean isTerminalOrder(String status) {
        return TERMINAL_ORDER_STATUSES.contains(status);
    }

    private double calculateRouteDistance(VehicleEntity vehicle, ParkStationResponse station) {
        Optional<com.fsd.dispatch.geo.ParkGeoTransformService.ParkPoint> parkPoint =
                vehiclePositionResolver.toPark(vehicle);
        if (parkPoint.isEmpty()) {
            return Double.MAX_VALUE;
        }
        BigDecimal currentX = parkPoint.get().x();
        BigDecimal currentY = parkPoint.get().y();
        try {
            Long parkId = station.getParkId() != null
                    ? station.getParkId()
                    : parkStationService.requireDefaultPark().getId();
            return calculatePathLength(parkRoutePlannerService.buildRoute(
                    parkId, currentX, currentY, station.getX(), station.getY()));
        } catch (BusinessException ex) {
            double dx = currentX.doubleValue() - station.getX().doubleValue();
            double dy = currentY.doubleValue() - station.getY().doubleValue();
            return Math.hypot(dx, dy);
        }
    }

    private boolean matchesParkOrder(OrderEntity order, Long defaultParkId, Set<Long> defaultParkStationIds) {
        if (order.getParkId() != null && !defaultParkId.equals(order.getParkId())) {
            return false;
        }
        return defaultParkStationIds.contains(order.getPickupPointId())
                && defaultParkStationIds.contains(order.getDropoffPointId());
    }

    private double calculatePathLength(List<ParkPointResponse> route) {
        if (route == null || route.size() < 2) {
            return 0D;
        }
        double total = 0D;
        ParkPointResponse previous = route.get(0);
        for (int i = 1; i < route.size(); i++) {
            ParkPointResponse current = route.get(i);
            // Phase 4：当两端均携带 GPS 时用 haversine（米），否则回退 schematic 欧几里得。
            if (previous.getLongitude() != null && previous.getLatitude() != null
                    && current.getLongitude() != null && current.getLatitude() != null) {
                total += com.fsd.dispatch.geo.GeoPolygonUtils.haversineMeters(
                        new com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint(
                                previous.getLongitude(), previous.getLatitude()),
                        new com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint(
                                current.getLongitude(), current.getLatitude()));
            } else {
                total += Math.hypot(
                        current.getX().doubleValue() - previous.getX().doubleValue(),
                        current.getY().doubleValue() - previous.getY().doubleValue());
            }
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

    @Override
    public List<ParkGeofenceResponse> listGeofences(Long parkId) {
        Long effectiveParkId = parkId != null ? parkId : parkStationService.requireDefaultPark().getId();
        return parkGeofenceService.listActiveByPark(effectiveParkId);
    }

    @Override
    public List<ParkOverviewResponse> listParkOverview() {
        List<ParkVehicleSnapshotResponse> vehicles = listVehicleSnapshots();
        ParkEntity defaultPark = parkStationService.requireDefaultPark();
        return parkStationService.listActiveParks().stream()
                .map(park -> {
                    ParkEntity entity = parkStationService.requirePark(park.getParkId());
                    boolean isDefault = defaultPark.getId().equals(park.getParkId());
                    List<ParkVehicleSnapshotResponse> parkVehicles = isDefault ? vehicles : List.of();
                    long online = parkVehicles.stream()
                            .filter(v -> "ONLINE".equals(v.getOnlineStatus()))
                            .count();
                    long busy = parkVehicles.stream()
                            .filter(v -> "BUSY".equals(v.getDispatchStatus()))
                            .count();
                    return ParkOverviewResponse.builder()
                            .parkId(park.getParkId())
                            .parkCode(park.getParkCode())
                            .parkName(park.getParkName())
                            .centerLng(entity.getCenterLng())
                            .centerLat(entity.getCenterLat())
                            .mapProvider(entity.getMapProvider())
                            .vehicleCount(parkVehicles.size())
                            .onlineCount(online)
                            .busyCount(busy)
                            .build();
                })
                .toList();
    }
}
