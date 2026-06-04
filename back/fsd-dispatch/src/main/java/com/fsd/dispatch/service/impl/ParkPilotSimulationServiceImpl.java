package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.fleet.service.FleetSnapshotAssembler;
import com.fsd.dispatch.entity.BatterySwapCabinetEntity;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.fleet.PilotFleetSupport;
import com.fsd.dispatch.fleet.simulation.SimulationFleetAdapter;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.fleet.simulation.SimulationMotionStore;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkingFacilityService;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteFollower;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteService;
import com.fsd.dispatch.geo.local.StationRoadSnapService;
import com.fsd.dispatch.mapf.MapfRoutePlanResult;
import com.fsd.dispatch.mapf.MapfRoutePlannerService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkPilotSimulationServiceImpl implements ParkPilotSimulationService {

    private static final List<String> DISPATCH_DEMAND_STATUSES = List.of(
            DispatchTaskStatus.PENDING.name(),
            DispatchTaskStatus.ASSIGNING.name(),
            DispatchTaskStatus.MANUAL_PENDING.name(),
            DispatchTaskStatus.ASSIGNED.name(),
            DispatchTaskStatus.EXECUTING.name());
    private static final List<String> ACTIVE_VEHICLE_TASK_STATUSES = List.of(
            DispatchTaskStatus.ASSIGNING.name(),
            DispatchTaskStatus.ASSIGNED.name(),
            DispatchTaskStatus.EXECUTING.name());

    private final ParkPilotProperties parkPilotProperties;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final FleetChargePolicy fleetChargePolicy;
    private final VehicleMapper vehicleMapper;
    private final VehicleService vehicleService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final OrderStateService orderStateService;
    private final VehicleReportService vehicleReportService;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final ParkStationService parkStationService;
    private final ParkingFacilityService parkingFacilityService;
    private final SimulationMotionStore simulationMotionStore;
    private final FleetAdapterRegistry fleetAdapterRegistry;
    private final SimulationFleetAdapter simulationFleetAdapter;
    private final FleetSnapshotAssembler fleetSnapshotAssembler;
    private final FleetRuntimeService fleetRuntimeService;
    private final DispatchStrategyRuntimeService strategyRuntimeService;
    private final BatterySwapSessionService batterySwapSessionService;
    private final BatterySwapCabinetMapper batterySwapCabinetMapper;
    private final DispatchAutomationRuleService automationRuleService;
    private final MapfRoutePlannerService mapfRoutePlannerService;
    private final RoadRouteService roadRouteService;
    private final ParkGeoTransformService parkGeoTransformService;
    private final StationRoadSnapService stationRoadSnapService;
    private boolean dispatchDemandActive;

    private List<ParkPointResponse> zjfChargingSpots;

    public ParkPilotSimulationServiceImpl(ParkPilotProperties parkPilotProperties,
                                          FleetEnergyProperties fleetEnergyProperties,
                                          FleetChargePolicy fleetChargePolicy,
                                          VehicleMapper vehicleMapper,
                                          VehicleService vehicleService,
                                          DispatchTaskMapper dispatchTaskMapper,
                                          OrderStateService orderStateService,
                                          VehicleReportService vehicleReportService,
                                          ParkRoutePlannerService parkRoutePlannerService,
                                          ParkStationService parkStationService,
                                          ParkingFacilityService parkingFacilityService,
                                          SimulationMotionStore simulationMotionStore,
                                          FleetAdapterRegistry fleetAdapterRegistry,
                                          SimulationFleetAdapter simulationFleetAdapter,
                                          FleetSnapshotAssembler fleetSnapshotAssembler,
                                          FleetRuntimeService fleetRuntimeService,
                                          DispatchStrategyRuntimeService strategyRuntimeService,
                                          BatterySwapSessionService batterySwapSessionService,
                                          BatterySwapCabinetMapper batterySwapCabinetMapper,
                                          DispatchAutomationRuleService automationRuleService,
                                          MapfRoutePlannerService mapfRoutePlannerService,
                                          RoadRouteService roadRouteService,
                                          ParkGeoTransformService parkGeoTransformService,
                                          StationRoadSnapService stationRoadSnapService) {
        this.parkPilotProperties = parkPilotProperties;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.fleetChargePolicy = fleetChargePolicy;
        this.vehicleMapper = vehicleMapper;
        this.vehicleService = vehicleService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.orderStateService = orderStateService;
        this.vehicleReportService = vehicleReportService;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.parkStationService = parkStationService;
        this.parkingFacilityService = parkingFacilityService;
        this.simulationMotionStore = simulationMotionStore;
        this.fleetAdapterRegistry = fleetAdapterRegistry;
        this.simulationFleetAdapter = simulationFleetAdapter;
        this.fleetSnapshotAssembler = fleetSnapshotAssembler;
        this.fleetRuntimeService = fleetRuntimeService;
        this.strategyRuntimeService = strategyRuntimeService;
        this.batterySwapSessionService = batterySwapSessionService;
        this.batterySwapCabinetMapper = batterySwapCabinetMapper;
        this.automationRuleService = automationRuleService;
        this.mapfRoutePlannerService = mapfRoutePlannerService;
        this.roadRouteService = roadRouteService;
        this.parkGeoTransformService = parkGeoTransformService;
        this.stationRoadSnapService = stationRoadSnapService;
    }

    @Override
    @Transactional
    public void initializeVehiclesIfNeeded() {
        if (!parkPilotProperties.isEnabled() || !parkPilotProperties.getSimulation().isEnabled()) {
            return;
        }
        ensurePilotFleet(
                PilotFleetSupport.SCHEMATIC_VEHICLE_PREFIX,
                parkPilotProperties.getSimulation().getVehicleCount(),
                true);
        ensurePilotFleet(
                PilotFleetSupport.GEO_VEHICLE_PREFIX,
                parkPilotProperties.getSimulation().getGeoVehicleCount(),
                false);
    }

    private void ensurePilotFleet(String prefix, int targetCount, boolean schematic) {
        List<VehicleEntity> existing = listPilotVehicles(prefix);
        for (int i = existing.size(); i < targetCount; i++) {
            ParkPointResponse spawn = schematic ? getStandbySpot(i) : getGeoStandbySpot(i);
            VehicleEntity vehicle = new VehicleEntity();
            vehicle.setVehicleCode(prefix + String.format("%02d", i + 1));
            vehicle.setVehicleName((schematic ? "园区仿真车 " : "短驳仿真车 ") + (i + 1));
            vehicle.setVehicleType("L4_DELIVERY");
            vehicle.setLinkMode(VehicleLinkMode.SIM.name());
            vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
            vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
            vehicle.setCurrentLongitude(spawn.getX());
            vehicle.setCurrentLatitude(spawn.getY());
            vehicle.setBatteryLevel(ThreadLocalRandom.current().nextInt(80, 101));
            vehicle.setLastReportTime(LocalDateTime.now());
            vehicle.setRemark(schematic ? "park-pilot-schematic" : "park-pilot-geo");
            vehicle.setVersion(0);
            vehicle.setDeleted(0);
            vehicleMapper.insert(vehicle);
            simulationMotionStore.put(vehicle.getId(), createIdleState(vehicle, i, schematic));
            simulationFleetAdapter.publishTelemetry(vehicle, simulationMotionStore.get(vehicle.getId()));
        }
    }

    @Override
    public List<ParkVehicleSnapshotResponse> buildSnapshots(List<VehicleEntity> vehicles) {
        return vehicles.stream()
                .sorted(Comparator.comparing(VehicleEntity::getVehicleCode))
                .map(vehicle -> {
                    SimulationMotionState motion = simulationMotionStore.get(vehicle.getId());
                    if (motion != null) {
                        simulationFleetAdapter.publishTelemetry(vehicle, motion);
                    }
                    return fleetSnapshotAssembler.assemble(vehicle,
                            fleetRuntimeService.get(vehicle.getId()).orElse(null));
                })
                .toList();
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${fsd.park.simulation.tick-interval-ms:1000}")
    @Transactional
    public void tick() {
        if (!parkPilotProperties.isEnabled() || !parkPilotProperties.getSimulation().isEnabled()) {
            return;
        }
        initializeVehiclesIfNeeded();
        dispatchDemandActive = hasDispatchDemand();
        for (VehicleEntity vehicle : listPilotVehicles()) {
            tickVehicle(vehicle);
        }
        if (dispatchDemandActive) {
            if (hasFleetDispatchDemand(false) && !fleetHasAssignableVehicle(PilotFleetSupport.SCHEMATIC_VEHICLE_PREFIX)) {
                recoverFleetUnderDispatchPressure(PilotFleetSupport.SCHEMATIC_VEHICLE_PREFIX);
            }
            if (hasFleetDispatchDemand(true) && !fleetHasAssignableVehicle(PilotFleetSupport.GEO_VEHICLE_PREFIX)) {
                recoverFleetUnderDispatchPressure(PilotFleetSupport.GEO_VEHICLE_PREFIX);
            }
        }
    }

    private void tickVehicle(VehicleEntity vehicle) {
        reconcileStaleVehicleAssignment(vehicle);
        SimulationMotionState state = simulationMotionStore.getOrCreate(vehicle.getId(),
                () -> createIdleState(vehicle, extractVehicleIndex(vehicle),
                        PilotFleetSupport.isSchematicPilotVehicle(vehicle)));
        if (handleCriticalBattery(vehicle, state)) {
            publishTelemetry(vehicle, state);
            return;
        }
        if (hasActiveAssignment(vehicle)) {
            if (!VehicleDispatchStatus.BUSY.name().equals(vehicle.getDispatchStatus())) {
                vehicle.setDispatchStatus(VehicleDispatchStatus.BUSY.name());
            }
            syncBusyStateIfNeeded(vehicle, state);
            processBusyVehicle(vehicle, state);
            publishTelemetry(vehicle, state);
            return;
        }
        if (state.offlineUntil != null && state.offlineUntil.isAfter(LocalDateTime.now())) {
            vehicle.setOnlineStatus(VehicleOnlineStatus.OFFLINE.name());
            vehicle.setLastReportTime(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
            recordPoint(state, vehicle);
            publishTelemetry(vehicle, state);
            return;
        }
        state.offlineUntil = null;
        processIdleVehicle(vehicle, state);
        publishTelemetry(vehicle, state);
    }

    private void publishTelemetry(VehicleEntity vehicle, SimulationMotionState state) {
        simulationFleetAdapter.publishTelemetry(vehicle, state);
    }

    private void processIdleVehicle(VehicleEntity vehicle, SimulationMotionState state) {
        if (hasActiveAssignment(vehicle)) {
            vehicle.setDispatchStatus(VehicleDispatchStatus.BUSY.name());
            syncBusyStateIfNeeded(vehicle, state);
            processBusyVehicle(vehicle, state);
            return;
        }
        clearStaleAssignmentRefs(vehicle);
        maybeGoOffline(state);
        vehicle.setOnlineStatus(state.offlineUntil == null ? VehicleOnlineStatus.ONLINE.name() : VehicleOnlineStatus.OFFLINE.name());
        if (state.offlineUntil == null) {
            vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
            automationRuleService.evaluateSimulationTick(defaultParkId(), vehicle, state);
            processIdleStage(vehicle, state);
        }
        vehicle.setLastReportTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        recordPoint(state, vehicle);
    }

    private void processBusyVehicle(VehicleEntity vehicle, SimulationMotionState state) {
        vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
        vehicle.setDispatchStatus(VehicleDispatchStatus.BUSY.name());
        switch (state.stage) {
            case "TO_PICKUP" -> {
                moveVehicleAlongRoute(vehicle, state);
                if (state.routeIndex >= state.route.size()) {
                    submitVehicleReport(vehicle, "START_EXECUTE", "Arrived at pickup station");
                    state.stage = "LOADING";
                    state.holdUntil = LocalDateTime.now().plusSeconds(3);
                    rotateDropoffTarget(state, vehicle);
                }
            }
            case "LOADING" -> {
                if (state.holdUntil != null && !LocalDateTime.now().isBefore(state.holdUntil)) {
                    state.stage = "TO_DROPOFF";
                }
            }
            case "TO_DROPOFF" -> {
                moveVehicleAlongRoute(vehicle, state);
                if (state.routeIndex >= state.route.size()) {
                    state.stage = "UNLOADING";
                    state.holdUntil = LocalDateTime.now().plusSeconds(3);
                }
            }
            case "UNLOADING" -> {
                if (state.holdUntil != null && !LocalDateTime.now().isBefore(state.holdUntil)) {
                    submitVehicleReport(vehicle, "TASK_SUCCESS", "Delivery completed");
                    VehicleEntity latest = vehicleMapper.selectById(vehicle.getId());
                    vehicle.setDispatchStatus(latest.getDispatchStatus());
                    vehicle.setCurrentTaskId(latest.getCurrentTaskId());
                    vehicle.setCurrentOrderId(latest.getCurrentOrderId());
                    state.taskId = null;
                    state.orderId = null;
                    state.holdUntil = null;
                    routeAfterDelivery(vehicle, state);
                }
            }
            default -> state.stage = "TO_PICKUP";
        }
        reduceBattery(vehicle, true, state);
        vehicle.setLastReportTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        recordPoint(state, vehicle);
    }

    private boolean hasActiveAssignment(VehicleEntity vehicle) {
        return vehicle.getCurrentTaskId() != null && vehicle.getCurrentOrderId() != null;
    }

    private void clearStaleAssignmentRefs(VehicleEntity vehicle) {
        if (vehicle.getCurrentTaskId() != null || vehicle.getCurrentOrderId() != null) {
            vehicle.setCurrentTaskId(null);
            vehicle.setCurrentOrderId(null);
        }
    }

    /**
     * 任务已结束/人工待处理但车辆仍占用 currentTaskId 时，释放为 IDLE，避免可派车长期为 0。
     */
    private void reconcileStaleVehicleAssignment(VehicleEntity vehicle) {
        Long taskId = vehicle.getCurrentTaskId();
        if (taskId == null) {
            if (vehicle.getCurrentOrderId() != null) {
                vehicle.setCurrentOrderId(null);
                vehicleMapper.updateById(vehicle);
            }
            return;
        }
        DispatchTaskEntity task = dispatchTaskMapper.selectById(taskId);
        boolean stale = task == null
                || Integer.valueOf(1).equals(task.getDeleted())
                || !ACTIVE_VEHICLE_TASK_STATUSES.contains(task.getStatus());
        if (!stale) {
            return;
        }
        vehicleService.releaseVehicle(vehicle.getId(), VehicleDispatchStatus.IDLE.name());
        VehicleEntity refreshed = vehicleMapper.selectById(vehicle.getId());
        if (refreshed != null) {
            vehicle.setDispatchStatus(refreshed.getDispatchStatus());
            vehicle.setCurrentTaskId(refreshed.getCurrentTaskId());
            vehicle.setCurrentOrderId(refreshed.getCurrentOrderId());
        }
        SimulationMotionState state = simulationMotionStore.get(vehicle.getId());
        if (state != null && isIdleLikeMotionStage(state.stage)) {
            state.taskId = null;
            state.orderId = null;
        }
    }

    private boolean fleetHasAssignableVehicle(String prefix) {
        return vehicleService.listAssignableVehicles().stream()
                .filter(vehicle -> vehicle.getVehicleCode() != null && vehicle.getVehicleCode().startsWith(prefix))
                .anyMatch(fleetChargePolicy::isAssignable);
    }

    private boolean hasFleetDispatchDemand(boolean geoFleet) {
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .in(DispatchTaskEntity::getStatus, DISPATCH_DEMAND_STATUSES))
                .stream()
                .anyMatch(task -> {
                    if (task.getOrderId() == null) {
                        return false;
                    }
                    try {
                        OrderEntity order = orderStateService.getOrder(task.getOrderId());
                        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
                        ParkStationResponse dropoff = parkStationService.requireStation(order.getDropoffPointId());
                        boolean geoOrder = PilotFleetSupport.isGeoDeliveryStation(pickup)
                                || PilotFleetSupport.isGeoDeliveryStation(dropoff);
                        return geoFleet == geoOrder;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                });
    }

    /** 派单积压且无可派车时，仿真车快速恢复至可派单 SOC 并退出 WAIT_CHARGING。 */
    private void recoverFleetUnderDispatchPressure(String prefix) {
        int targetSoc = fleetEnergyProperties.getMinAssignableSoc();
        for (VehicleEntity vehicle : listPilotVehicles(prefix)) {
            if (!VehicleOnlineStatus.ONLINE.name().equals(vehicle.getOnlineStatus())
                    || !VehicleDispatchStatus.IDLE.name().equals(vehicle.getDispatchStatus())) {
                continue;
            }
            int soc = normalizeBattery(vehicle.getBatteryLevel());
            SimulationMotionState state = simulationMotionStore.get(vehicle.getId());
            if (soc >= targetSoc && fleetChargePolicy.isAssignable(vehicle)) {
                continue;
            }
            if (state != null) {
                if ("WAIT_CHARGING".equals(state.stage)) {
                    state.stage = "STANDBY";
                    parkingFacilityService.releaseReservation(vehicle.getId());
                }
                if (fleetChargePolicy.isActivelyCharging(state.stage)
                        || fleetChargePolicy.isActivelySwapping(state.stage)) {
                    state.stage = "STANDBY";
                    state.route = List.of();
                    state.routeIndex = 0;
                    state.pluggedIn = false;
                    parkingFacilityService.releaseByVehicle(vehicle.getId());
                }
            }
            vehicle.setBatteryLevel(Math.min(
                    fleetEnergyProperties.getFullSoc(),
                    Math.max(targetSoc, soc + fleetEnergyProperties.getChargeRatePerTick() * 2)));
            vehicleMapper.updateById(vehicle);
        }
    }

    private void syncBusyStateIfNeeded(VehicleEntity vehicle, SimulationMotionState state) {
        if (needsBusyResync(vehicle, state)) {
            syncBusyState(vehicle, state);
        }
    }

    private boolean needsBusyResync(VehicleEntity vehicle, SimulationMotionState state) {
        if (!Objects.equals(state.taskId, vehicle.getCurrentTaskId())
                || !Objects.equals(state.orderId, vehicle.getCurrentOrderId())) {
            return true;
        }
        return isIdleLikeMotionStage(state.stage);
    }

    private boolean isIdleLikeMotionStage(String stage) {
        return stage == null
                || "STANDBY".equals(stage)
                || "OFFLINE".equals(stage)
                || "CHARGING".equals(stage)
                || "TO_CHARGING".equals(stage)
                || "WAIT_CHARGING".equals(stage)
                || "TO_SWAP".equals(stage)
                || "SWAPPING".equals(stage)
                || "RETURNING_TO_STANDBY".equals(stage)
                || "EMERGENCY_PARKING".equals(stage);
    }

    private void syncBusyState(VehicleEntity vehicle, SimulationMotionState state) {
        OrderEntity order = orderStateService.getOrder(vehicle.getCurrentOrderId());
        ParkStationResponse pickup = getStation(order.getPickupPointId());
        ParkStationResponse dropoff = getStation(order.getDropoffPointId());
        state.stage = "TO_PICKUP";
        state.taskId = vehicle.getCurrentTaskId();
        state.orderId = vehicle.getCurrentOrderId();
        state.targetX = pickup.getX();
        state.targetY = pickup.getY();
        state.targetCode = pickup.getStationCode();
        state.targetType = "PICKUP";
        state.nextTargetX = dropoff.getX();
        state.nextTargetY = dropoff.getY();
        state.nextTargetCode = dropoff.getStationCode();
        state.nextTargetType = "DROPOFF";
        state.route = planRoute(vehicle.getId(),
                vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude(),
                pickup.getX(), pickup.getY());
        state.routeIndex = 1;
        if (PilotFleetSupport.isGeoPilotVehicle(vehicle)) {
            beginGeoRoute(vehicle, state, resolveGeo(vehicle), resolveStationGeo(pickup));
        } else {
            state.geoFollower = null;
            state.plannedGeoPolyline = List.of();
            state.routeSource = null;
            state.routeInvalid = false;
        }
        state.holdUntil = null;
        state.offlineUntil = null;
        state.busyMoveTicks = 0;
        state.pluggedIn = false;
        parkingFacilityService.releaseByVehicle(vehicle.getId());
    }

    private void rotateDropoffTarget(SimulationMotionState state, VehicleEntity vehicle) {
        state.targetX = state.nextTargetX;
        state.targetY = state.nextTargetY;
        state.targetCode = state.nextTargetCode;
        state.targetType = state.nextTargetType;
        state.route = planRoute(vehicle.getId(), state.lastX, state.lastY, state.targetX, state.targetY);
        state.routeIndex = 1;
        if (PilotFleetSupport.isGeoPilotVehicle(vehicle)) {
            beginGeoRouteForTarget(state, state.lastX, state.lastY, state.targetX, state.targetY);
        } else {
            state.geoFollower = null;
            state.plannedGeoPolyline = List.of();
            state.routeSource = null;
            state.routeInvalid = false;
        }
    }

    private void routeAfterDelivery(VehicleEntity vehicle, SimulationMotionState state) {
        if (shouldReturnToCharge(vehicle)) {
            routeToEnergyRecovery(vehicle, state);
            return;
        }
        if (shouldPreferCharging()) {
            if (isChargeSessionComplete(vehicle)) {
                enterPluggedInStandby(vehicle, state);
            } else {
                routeToCharging(vehicle, state);
            }
            return;
        }
        routeToStandby(vehicle, state);
    }

    private boolean shouldPreferCharging() {
        return fleetEnergyProperties.isIdleChargeWhenNoDemand() && !dispatchDemandActive;
    }

    private boolean hasDispatchDemand() {
        Long count = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .in(DispatchTaskEntity::getStatus, DISPATCH_DEMAND_STATUSES));
        return count != null && count > 0;
    }

    private ParkStationResponse getStation(Long stationId) {
        return parkStationService.requireStation(stationId);
    }

    private void maybeGoOffline(SimulationMotionState state) {
        if (!dispatchDemandActive || state.pluggedIn) {
            return;
        }
        if (state.offlineUntil == null
                && "STANDBY".equals(state.stage)
                && ThreadLocalRandom.current().nextDouble() < parkPilotProperties.getSimulation().getOfflineProbability()) {
            state.offlineUntil = LocalDateTime.now().plusSeconds(parkPilotProperties.getSimulation().getOfflineDurationSeconds());
            state.stage = "OFFLINE";
        }
    }

    private void processIdleStage(VehicleEntity vehicle, SimulationMotionState state) {
        redirectIdleVehicleIfNeeded(vehicle, state);
        switch (state.stage) {
            case "WAIT_CHARGING" -> routeToCharging(vehicle, state);
            case "TO_CHARGING" -> {
                moveVehicleAlongRoute(vehicle, state);
                if (state.routeIndex >= state.route.size()) {
                    state.stage = "CHARGING";
                    state.route = List.of();
                    state.routeIndex = 0;
                    state.holdUntil = null;
                    bindCharging(vehicle, state);
                }
            }
            case "CHARGING" -> {
                recoverBattery(vehicle);
                if (dispatchDemandActive && !fleetChargePolicy.isAssignable(vehicle)) {
                    recoverBattery(vehicle);
                }
                if (isChargeSessionComplete(vehicle)) {
                    enterPluggedInStandby(vehicle, state);
                }
            }
            case "EMERGENCY_PARKING" -> {
                vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
            }
            case "TO_SWAP" -> {
                moveVehicleAlongRoute(vehicle, state);
                if (state.routeIndex >= state.route.size()) {
                    state.stage = "SWAPPING";
                    state.swapTicks = 0;
                    bindSwap(vehicle, state);
                }
            }
            case "SWAPPING" -> {
                state.swapTicks++;
                if (state.swapTicks >= fleetEnergyProperties.getSwapDurationTicks()) {
                    completeSwap(vehicle, state);
                    routeToStandby(vehicle, state);
                }
            }
            case "RETURNING_TO_STANDBY" -> {
                if (shouldPreferCharging() && !isChargeSessionComplete(vehicle)) {
                    routeToCharging(vehicle, state);
                    break;
                }
                moveVehicleAlongRoute(vehicle, state);
                reduceBattery(vehicle, false, state);
                if (state.routeIndex >= state.route.size()) {
                    state.pluggedIn = false;
                    state.stage = "STANDBY";
                    state.route = List.of();
                    state.routeIndex = 0;
                }
            }
            case "STANDBY" -> {
                if (isPluggedInStandby(vehicle, state)) {
                    holdPluggedInStandby(vehicle, state);
                    break;
                }
                if (needsCharging(vehicle, state)) {
                    routeToEnergyRecovery(vehicle, state);
                    break;
                }
                ensureStandbyLocation(vehicle, state);
                if (state.route != null && state.routeIndex < state.route.size()) {
                    moveVehicleAlongRoute(vehicle, state);
                }
                reduceBattery(vehicle, false, state);
            }
            default -> {
                if (shouldPreferCharging()) {
                    routeToCharging(vehicle, state);
                } else {
                    routeToStandby(vehicle, state);
                }
                processIdleStage(vehicle, state);
            }
        }
    }

    private void redirectIdleVehicleIfNeeded(VehicleEntity vehicle, SimulationMotionState state) {
        if (isChargingStage(state.stage) || "RETURNING_TO_STANDBY".equals(state.stage)) {
            return;
        }
        if (isPluggedInStandby(vehicle, state)) {
            return;
        }
        if (needsCharging(vehicle, state)) {
            routeToEnergyRecovery(vehicle, state);
        }
    }

    private void routeToEnergyRecovery(VehicleEntity vehicle, SimulationMotionState state) {
        String mode = resolveEnergyRecoveryMode();
        if ("SWAP".equalsIgnoreCase(mode) || ("AUTO".equalsIgnoreCase(mode) && vehicle.getId() % 2 == 0)) {
            routeToSwap(vehicle, state);
        } else {
            routeToCharging(vehicle, state);
        }
    }

    private String resolveEnergyRecoveryMode() {
        FleetEnergyProperties energy = strategyRuntimeService.energyForAssign(defaultParkId());
        return energy.getEnergyRecoveryMode() == null ? "CHARGE" : energy.getEnergyRecoveryMode();
    }

    private void routeToSwap(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        state.busyMoveTicks = 0;
        state.pluggedIn = false;
        parkingFacilityService.releaseByVehicle(vehicle.getId());
        BatterySwapCabinetEntity cabinet = findSwapCabinet(defaultParkId());
        if (cabinet == null) {
            routeToCharging(vehicle, state);
            return;
        }
        state.swapPoint = ParkPointResponse.builder()
                .code(cabinet.getCabinetCode())
                .x(cabinet.getCoordX())
                .y(cabinet.getCoordY())
                .build();
        state.stage = "TO_SWAP";
        routeToTarget(vehicle, state, state.swapPoint, "SWAP");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "SWAPPING";
            state.swapTicks = 0;
            bindSwap(vehicle, state);
        }
    }

    private BatterySwapCabinetEntity findSwapCabinet(Long parkId) {
        return batterySwapCabinetMapper.selectOne(new LambdaQueryWrapper<BatterySwapCabinetEntity>()
                .eq(BatterySwapCabinetEntity::getDeleted, 0)
                .eq(BatterySwapCabinetEntity::getStatus, "ACTIVE")
                .eq(BatterySwapCabinetEntity::getParkId, parkId)
                .last("limit 1"));
    }

    private void bindSwap(VehicleEntity vehicle, SimulationMotionState state) {
        if (state.swapPoint == null) {
            return;
        }
        BatterySwapCabinetEntity cabinet = findSwapCabinet(defaultParkId());
        if (cabinet == null) {
            return;
        }
        batterySwapSessionService.startSession(defaultParkId(), vehicle.getId(), cabinet.getId(),
                vehicle.getBatteryLevel() == null ? 0 : vehicle.getBatteryLevel());
    }

    private void completeSwap(VehicleEntity vehicle, SimulationMotionState state) {
        batterySwapSessionService.completeActiveSession(vehicle.getId());
        vehicle.setBatteryLevel(fleetEnergyProperties.getFullSoc());
        state.stage = "STANDBY";
        state.swapTicks = 0;
    }

    private boolean needsCharging(VehicleEntity vehicle, SimulationMotionState state) {
        if (shouldReturnToCharge(vehicle)) {
            return true;
        }
        if (!shouldPreferCharging()) {
            return false;
        }
        return !isChargeSessionComplete(vehicle) || !state.pluggedIn;
    }

    private boolean isPluggedInStandby(VehicleEntity vehicle, SimulationMotionState state) {
        return state.pluggedIn && isFullyCharged(vehicle) && "STANDBY".equals(state.stage);
    }

    private void enterPluggedInStandby(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        state.pluggedIn = true;
        state.stage = "STANDBY";
        state.route = List.of();
        state.routeIndex = 0;
        state.targetCode = state.chargingPoint.getCode();
        state.targetType = "STANDBY";
        state.targetX = state.chargingPoint.getX();
        state.targetY = state.chargingPoint.getY();
        vehicle.setCurrentLongitude(state.chargingPoint.getX());
        vehicle.setCurrentLatitude(state.chargingPoint.getY());
        bindPluggedStandby(vehicle, state);
    }

    private void holdPluggedInStandby(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        vehicle.setCurrentLongitude(state.chargingPoint.getX());
        vehicle.setCurrentLatitude(state.chargingPoint.getY());
        state.targetCode = state.chargingPoint.getCode();
        state.targetType = "STANDBY";
        state.targetX = state.chargingPoint.getX();
        state.targetY = state.chargingPoint.getY();
    }

    private boolean isActivelyCharging(SimulationMotionState state) {
        return fleetChargePolicy.isActivelyCharging(state.stage);
    }

    private boolean shouldReturnToCharge(VehicleEntity vehicle) {
        return fleetChargePolicy.shouldReturnToCharge(vehicle.getBatteryLevel());
    }

    private boolean isChargeSessionComplete(VehicleEntity vehicle) {
        return fleetChargePolicy.isChargeSessionComplete(vehicle.getBatteryLevel());
    }

    private boolean isFullyCharged(VehicleEntity vehicle) {
        return fleetChargePolicy.isFullyCharged(vehicle.getBatteryLevel());
    }

    private boolean handleCriticalBattery(VehicleEntity vehicle, SimulationMotionState state) {
        if (!fleetChargePolicy.isCriticalSoc(vehicle.getBatteryLevel())) {
            return false;
        }
        if (hasActiveAssignment(vehicle) || isChargingStage(state.stage)) {
            return false;
        }
        state.stage = "EMERGENCY_PARKING";
        state.route = List.of();
        state.routeIndex = 0;
        vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
        vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
        vehicle.setLastReportTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        recordPoint(state, vehicle);
        return true;
    }

    private boolean isChargingStage(String stage) {
        return fleetChargePolicy.isActivelyCharging(stage) || fleetChargePolicy.isActivelySwapping(stage);
    }

    private void submitVehicleReport(VehicleEntity vehicle, String reportType, String message) {
        VehicleReportRequest request = new VehicleReportRequest();
        request.setVehicleCode(vehicle.getVehicleCode());
        request.setOnlineStatus(vehicle.getOnlineStatus());
        request.setDispatchStatus(vehicle.getDispatchStatus());
        request.setTaskId(vehicle.getCurrentTaskId());
        request.setOrderId(vehicle.getCurrentOrderId());
        request.setReportType(reportType);
        request.setReportTime(LocalDateTime.now());
        request.setLatitude(vehicle.getCurrentLatitude());
        request.setLongitude(vehicle.getCurrentLongitude());
        request.setBatteryLevel(vehicle.getBatteryLevel());
        request.setResultMessage(message);
        vehicleReportService.handleReport(request);
    }

    private void moveVehicle(VehicleEntity vehicle, BigDecimal targetX, BigDecimal targetY, BigDecimal maxStep) {
        if (targetX == null || targetY == null || vehicle.getCurrentLongitude() == null || vehicle.getCurrentLatitude() == null) {
            return;
        }
        double currentX = vehicle.getCurrentLongitude().doubleValue();
        double currentY = vehicle.getCurrentLatitude().doubleValue();
        double dx = targetX.doubleValue() - currentX;
        double dy = targetY.doubleValue() - currentY;
        double distance = Math.hypot(dx, dy);
        if (distance <= maxStep.doubleValue()) {
            vehicle.setCurrentLongitude(targetX);
            vehicle.setCurrentLatitude(targetY);
            return;
        }
        double ratio = maxStep.doubleValue() / distance;
        vehicle.setCurrentLongitude(BigDecimal.valueOf(currentX + dx * ratio).setScale(3, RoundingMode.HALF_UP));
        vehicle.setCurrentLatitude(BigDecimal.valueOf(currentY + dy * ratio).setScale(3, RoundingMode.HALF_UP));
    }

    private void moveVehicleAlongRoute(VehicleEntity vehicle, SimulationMotionState state) {
        if (state.geoFollower != null && !state.geoFollower.isEmpty()) {
            advanceGeoAlongRoute(state);
            syncParkCoordsFromGeo(vehicle, state);
            if (state.geoFollower.isComplete()) {
                finishRouteSegment(vehicle, state);
            }
            return;
        }
        if (state.route == null || state.route.isEmpty() || state.routeIndex >= state.route.size()) {
            return;
        }
        ParkPointResponse nextPoint = state.route.get(state.routeIndex);
        moveVehicle(vehicle, nextPoint.getX(), nextPoint.getY(), parkPilotProperties.getVehicleSpeedPxPerSecond());
        if (isNear(vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude(), nextPoint.getX(), nextPoint.getY())) {
            vehicle.setCurrentLongitude(nextPoint.getX());
            vehicle.setCurrentLatitude(nextPoint.getY());
            state.routeIndex++;
        }
    }

    private void finishRouteSegment(VehicleEntity vehicle, SimulationMotionState state) {
        if (state.route != null && !state.route.isEmpty()) {
            ParkPointResponse end = state.route.get(state.route.size() - 1);
            vehicle.setCurrentLongitude(end.getX());
            vehicle.setCurrentLatitude(end.getY());
            state.routeIndex = state.route.size();
        }
        syncParkCoordsFromGeo(vehicle, state);
    }

    private void syncParkCoordsFromGeo(VehicleEntity vehicle, SimulationMotionState state) {
        if (state.geoLongitude == null || state.geoLatitude == null) {
            return;
        }
        parkGeoTransformService.fromGcj02(state.geoLongitude, state.geoLatitude).ifPresent(park -> {
            vehicle.setCurrentLongitude(park.x());
            vehicle.setCurrentLatitude(park.y());
        });
    }

    private void reduceBattery(VehicleEntity vehicle, boolean busy, SimulationMotionState state) {
        if (isChargingStage(state.stage) || isPluggedInStandby(vehicle, state)
                || "WAIT_CHARGING".equals(state.stage)) {
            return;
        }
        if ("LOADING".equals(state.stage) || "UNLOADING".equals(state.stage)) {
            return;
        }
        int current = normalizeBattery(vehicle.getBatteryLevel());
        int floor = fleetEnergyProperties.getReserveSocFloor();
        if (busy && state.geoFollower != null && !state.geoFollower.isEmpty()) {
            drainBatteryByGeoDistance(vehicle, state, current, floor);
            return;
        }
        if (busy) {
            state.busyMoveTicks = state.busyMoveTicks + 1;
            int interval = Math.max(1, fleetEnergyProperties.getBusyDrainIntervalTicks());
            if (state.busyMoveTicks % interval == 0) {
                current = Math.max(floor, current - 1);
            }
        } else if (ThreadLocalRandom.current().nextDouble() < fleetEnergyProperties.getIdleDrainProbability()) {
            current = Math.max(floor, current - 1);
        }
        vehicle.setBatteryLevel(current);
    }

    private void drainBatteryByGeoDistance(VehicleEntity vehicle, SimulationMotionState state, int current, int floor) {
        double traveled = state.geoFollower.traveledMeters();
        double metersPerPercent = Math.max(50D, fleetEnergyProperties.getBusyDrainMetersPerPercent());
        double delta = traveled - state.geoTraveledAtLastDrain;
        if (delta < metersPerPercent) {
            return;
        }
        int steps = (int) (delta / metersPerPercent);
        current = Math.max(floor, current - steps);
        vehicle.setBatteryLevel(current);
        state.geoTraveledAtLastDrain += steps * metersPerPercent;
    }

    private void recoverBattery(VehicleEntity vehicle) {
        int current = normalizeBattery(vehicle.getBatteryLevel());
        int fullLevel = fleetEnergyProperties.getFullSoc();
        vehicle.setBatteryLevel(Math.min(fullLevel, current + fleetEnergyProperties.getChargeRatePerTick()));
    }

    private int normalizeBattery(Integer batteryLevel) {
        return batteryLevel == null ? fleetEnergyProperties.getFullSoc() : batteryLevel;
    }

    private boolean isNear(BigDecimal currentX, BigDecimal currentY, BigDecimal targetX, BigDecimal targetY) {
        if (currentX == null || currentY == null || targetX == null || targetY == null) {
            return false;
        }
        return Math.hypot(currentX.doubleValue() - targetX.doubleValue(),
                currentY.doubleValue() - targetY.doubleValue()) <= parkPilotProperties.getVehicleSpeedPxPerSecond().doubleValue();
    }

    private void recordPoint(SimulationMotionState state, VehicleEntity vehicle) {
        state.lastX = vehicle.getCurrentLongitude();
        state.lastY = vehicle.getCurrentLatitude();
        ParkPointResponse.ParkPointResponseBuilder trailPoint = ParkPointResponse.builder()
                .code(null)
                .x(vehicle.getCurrentLongitude())
                .y(vehicle.getCurrentLatitude());
        if (state.geoLongitude != null && state.geoLatitude != null) {
            trailPoint.longitude(state.geoLongitude).latitude(state.geoLatitude);
            state.geoTrail.add(new GeoPoint(state.geoLongitude, state.geoLatitude));
            while (state.geoTrail.size() > parkPilotProperties.getSimulation().getMaxTrailSize()) {
                state.geoTrail.remove(0);
            }
        }
        state.trail.addLast(trailPoint.build());
        while (state.trail.size() > parkPilotProperties.getSimulation().getMaxTrailSize()) {
            state.trail.removeFirst();
        }
    }

    private void beginGeoRoute(VehicleEntity vehicle, SimulationMotionState state, GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            beginGeoRouteForTarget(state, vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude(),
                    state.targetX, state.targetY);
            return;
        }
        RoadRouteResult route = roadRouteService.planDrivingRoute(from, to);
        state.plannedGeoPolyline = route.polyline();
        state.routeSource = route.source().name();
        state.routeInvalid = route.invalid();
        if (route.invalid() || route.polyline().size() < 2) {
            state.geoFollower = null;
        } else {
            state.geoFollower = RoadRouteFollower.fromPolyline(route.polyline());
            state.geoFollower.reset();
            state.geoTraveledAtLastDrain = 0D;
        }
        syncGeoPosition(state);
    }

    private void beginGeoRouteForTarget(SimulationMotionState state,
                                        BigDecimal fromX, BigDecimal fromY,
                                        BigDecimal toX, BigDecimal toY) {
        GeoPoint from = snapGeo(parkGeoTransformService.toGcj02(fromX, fromY).orElse(null));
        GeoPoint to = snapGeo(parkGeoTransformService.toGcj02(toX, toY).orElse(null));
        if (from == null || to == null) {
            state.geoFollower = null;
            state.plannedGeoPolyline = List.of();
            state.routeSource = null;
            state.routeInvalid = false;
            return;
        }
        RoadRouteResult route = roadRouteService.planDrivingRoute(from, to);
        state.plannedGeoPolyline = route.polyline();
        state.routeSource = route.source().name();
        state.routeInvalid = route.invalid();
        if (route.invalid() || route.polyline().size() < 2) {
            state.geoFollower = null;
        } else {
            state.geoFollower = RoadRouteFollower.fromPolyline(route.polyline());
            state.geoFollower.reset();
            state.geoTraveledAtLastDrain = 0D;
        }
        syncGeoPosition(state);
    }

    private void advanceGeoAlongRoute(SimulationMotionState state) {
        if (state.geoFollower == null || state.geoFollower.isEmpty()) {
            return;
        }
        state.geoFollower.advanceMeters(geoMetersPerTick());
        syncGeoPosition(state);
    }

    private void syncGeoPosition(SimulationMotionState state) {
        if (state.geoFollower == null || state.geoFollower.isEmpty()) {
            return;
        }
        GeoPoint pos = state.geoFollower.currentPosition();
        if (pos == null) {
            return;
        }
        state.geoLongitude = pos.longitude();
        state.geoLatitude = pos.latitude();
        state.headingDegrees = state.geoFollower.headingDegrees();
    }

    private double geoMetersPerTick() {
        int mapWidth = parkPilotProperties.getWidth() == null ? 1200 : parkPilotProperties.getWidth();
        int widthMeters = parkPilotProperties.getGeo().getParkWidthMeters() == null
                ? 960 : parkPilotProperties.getGeo().getParkWidthMeters();
        double metersPerPx = widthMeters / (double) mapWidth;
        return parkPilotProperties.getVehicleSpeedPxPerSecond().doubleValue() * metersPerPx;
    }

    private GeoPoint resolveGeo(VehicleEntity vehicle) {
        if (vehicle.getCurrentLongitude() == null || vehicle.getCurrentLatitude() == null) {
            return null;
        }
        return snapGeo(parkGeoTransformService.toGcj02(vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude())
                .orElse(null));
    }

    private GeoPoint resolveStationGeo(ParkStationResponse station) {
        GeoPoint raw;
        if (station.getCoordLng() != null && station.getCoordLat() != null) {
            raw = new GeoPoint(station.getCoordLng(), station.getCoordLat());
        } else {
            raw = parkGeoTransformService.toGcj02(station.getX(), station.getY()).orElse(null);
        }
        return snapGeo(raw);
    }

    private GeoPoint snapGeo(GeoPoint point) {
        if (point == null) {
            return null;
        }
        return stationRoadSnapService.snapToNearestRoad(point).orElse(point);
    }

    private SimulationMotionState createIdleState(VehicleEntity vehicle, int index, boolean schematic) {
        SimulationMotionState state = new SimulationMotionState();
        state.standbyPoint = schematic ? getStandbySpot(index) : getGeoStandbySpot(index);
        state.chargingPoint = getChargingSpot(vehicle, index, schematic);
        state.stage = "STANDBY";
        state.targetCode = state.standbyPoint.getCode();
        state.targetType = "STANDBY";
        state.targetX = state.standbyPoint.getX();
        state.targetY = state.standbyPoint.getY();
        state.route = List.of();
        state.routeIndex = 0;
        recordPoint(state, vehicle);
        return state;
    }

    private ParkPointResponse getStandbySpot(int index) {
        List<ParkPointResponse> standbySpots = parkPilotProperties.getParkingSpots().stream()
                .map(point -> ParkPointResponse.builder()
                        .code(point.getCode())
                        .x(point.getX())
                        .y(point.getY())
                        .build())
                .toList();
        if (standbySpots.isEmpty()) {
            return ParkPointResponse.builder()
                    .code("P" + (index + 1))
                    .x(BigDecimal.valueOf(120 + index * 60L))
                    .y(BigDecimal.valueOf(700))
                    .build();
        }
        return standbySpots.get(index % standbySpots.size());
    }

    private ParkPointResponse getChargingSpot(VehicleEntity vehicle, int index, boolean schematic) {
        if (!schematic) {
            List<ParkPointResponse> chargingSpots = listZjfChargingSpots();
            if (!chargingSpots.isEmpty()) {
                return selectNearestChargingSpot(vehicle, chargingSpots)
                        .orElseGet(() -> chargingSpots.get(index % chargingSpots.size()));
            }
        }
        return getStandbySpot(index);
    }

    private Optional<ParkPointResponse> selectNearestChargingSpot(VehicleEntity vehicle,
                                                                  List<ParkPointResponse> chargingSpots) {
        GeoPoint position = resolveVehicleGeoPoint(vehicle);
        if (position == null) {
            return Optional.empty();
        }
        ParkPointResponse nearest = null;
        double bestMeters = Double.MAX_VALUE;
        for (ParkPointResponse spot : chargingSpots) {
            if (spot.getLongitude() == null || spot.getLatitude() == null) {
                continue;
            }
            GeoPoint target = new GeoPoint(spot.getLongitude(), spot.getLatitude());
            double meters = GeoPolygonUtils.haversineMeters(position, target);
            if (meters < bestMeters) {
                bestMeters = meters;
                nearest = spot;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private GeoPoint resolveVehicleGeoPoint(VehicleEntity vehicle) {
        Optional<FleetRuntime> runtime = fleetRuntimeService.get(vehicle.getId());
        if (runtime.isPresent()
                && runtime.get().getLongitude() != null
                && runtime.get().getLatitude() != null) {
            return new GeoPoint(runtime.get().getLongitude(), runtime.get().getLatitude());
        }
        SimulationMotionState state = simulationMotionStore.get(vehicle.getId());
        if (state == null) {
            return null;
        }
        BigDecimal parkX = state.lastX != null ? state.lastX : state.targetX;
        BigDecimal parkY = state.lastY != null ? state.lastY : state.targetY;
        return parkGeoTransformService.toGcj02(parkX, parkY).orElse(null);
    }

    private ParkPointResponse getGeoStandbySpot(int index) {
        try {
            List<ParkStationResponse> idleStations = parkStationService.listStations(defaultParkId()).stream()
                    .filter(station -> "ZJF-IDLE-01".equals(station.getStationCode()))
                    .toList();
            if (!idleStations.isEmpty()) {
                ParkStationResponse idle = idleStations.get(0);
                return ParkPointResponse.builder()
                        .code(idle.getStationCode())
                        .x(idle.getX())
                        .y(idle.getY())
                        .longitude(idle.getCoordLng())
                        .latitude(idle.getCoordLat())
                        .build();
            }
        } catch (RuntimeException ex) {
            // fall through
        }
        return getStandbySpot(index);
    }

    private List<ParkPointResponse> listZjfChargingSpots() {
        if (zjfChargingSpots != null) {
            return zjfChargingSpots;
        }
        try {
            Long parkId = defaultParkId();
            zjfChargingSpots = parkStationService.listStations(parkId).stream()
                    .filter(station -> station.getStationCode() != null
                            && station.getStationCode().startsWith("ZJF-CHG"))
                    .map(station -> ParkPointResponse.builder()
                            .code(station.getStationCode())
                            .x(station.getX())
                            .y(station.getY())
                            .longitude(station.getCoordLng())
                            .latitude(station.getCoordLat())
                            .build())
                    .toList();
        } catch (RuntimeException ex) {
            zjfChargingSpots = List.of();
        }
        return zjfChargingSpots;
    }

    private void routeToStandby(VehicleEntity vehicle, SimulationMotionState state) {
        if (shouldPreferCharging() && !state.pluggedIn) {
            routeToCharging(vehicle, state);
            return;
        }
        ensureStandbyLocation(vehicle, state);
        state.pluggedIn = false;
        state.stage = "RETURNING_TO_STANDBY";
        routeToTarget(vehicle, state, state.standbyPoint, "STANDBY");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "STANDBY";
        }
    }

    private void routeToCharging(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        state.busyMoveTicks = 0;
        state.pluggedIn = false;
        parkingFacilityService.releaseByVehicle(vehicle.getId());
        Long parkId = defaultParkId();
        String preferred = state.chargingPoint != null ? state.chargingPoint.getCode() : null;
        var reserved = parkingFacilityService.reserveChargingSlot(parkId, vehicle.getId(), preferred);
        if (reserved.isEmpty()) {
            state.stage = "WAIT_CHARGING";
            return;
        }
        state.chargingPoint = reserved.get();
        state.stage = "TO_CHARGING";
        routeToTarget(vehicle, state, state.chargingPoint, "CHARGING");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "CHARGING";
            bindCharging(vehicle, state);
        }
    }

    private void ensureStandbyLocation(VehicleEntity vehicle, SimulationMotionState state) {
        if (state.standbyPoint == null) {
            state.standbyPoint = PilotFleetSupport.isSchematicPilotVehicle(vehicle)
                    ? getStandbySpot(0)
                    : getGeoStandbySpot(0);
        }
        if (state.chargingPoint == null) {
            state.chargingPoint = getChargingSpot(vehicle, 0, PilotFleetSupport.isSchematicPilotVehicle(vehicle));
        }
    }

    private void routeToTarget(VehicleEntity vehicle, SimulationMotionState state, ParkPointResponse point, String targetType) {
        state.targetCode = point.getCode();
        state.targetType = targetType;
        state.targetX = point.getX();
        state.targetY = point.getY();
        if (state.lastX == null || state.lastY == null) {
            state.route = List.of(point);
            state.routeIndex = 0;
            return;
        }
        state.route = planRoute(vehicle.getId(), state.lastX, state.lastY, point.getX(), point.getY());
        state.routeIndex = state.route.size() > 1 ? 1 : state.route.size();
        if (PilotFleetSupport.isGeoPilotVehicle(vehicle)) {
            beginGeoRouteForTarget(state, state.lastX, state.lastY, point.getX(), point.getY());
        } else {
            state.geoFollower = null;
            state.plannedGeoPolyline = List.of();
            state.routeSource = null;
            state.routeInvalid = false;
        }
    }

    private List<ParkPointResponse> planRoute(Long vehicleId,
                                              BigDecimal startX, BigDecimal startY,
                                              BigDecimal endX, BigDecimal endY) {
        if (mapfRoutePlannerService.isEnabled() && vehicleId != null) {
            MapfRoutePlanResult plan = mapfRoutePlannerService.planAndReserve(
                    defaultParkId(), vehicleId, startX, startY, endX, endY);
            if (plan.isSuccess()) {
                return plan.getRoute();
            }
        }
        return parkRoutePlannerService.buildRoute(defaultParkId(), startX, startY, endX, endY);
    }

    private Long defaultParkId() {
        return parkStationService.requireDefaultPark().getId();
    }

    private void bindCharging(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        if (state.chargingPoint == null || state.chargingPoint.getCode() == null) {
            return;
        }
        parkingFacilityService.markCharging(defaultParkId(), vehicle.getId(), state.chargingPoint.getCode());
    }

    private void bindPluggedStandby(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(vehicle, state);
        if (state.chargingPoint == null || state.chargingPoint.getCode() == null) {
            return;
        }
        parkingFacilityService.occupyPluggedStandby(defaultParkId(), vehicle.getId(), state.chargingPoint.getCode());
    }

    private int extractVehicleIndex(VehicleEntity vehicle) {
        String code = vehicle.getVehicleCode();
        if (code == null) {
            return 0;
        }
        String numeric = null;
        if (code.startsWith(PilotFleetSupport.SCHEMATIC_VEHICLE_PREFIX)) {
            numeric = code.substring(PilotFleetSupport.SCHEMATIC_VEHICLE_PREFIX.length());
        } else if (code.startsWith(PilotFleetSupport.GEO_VEHICLE_PREFIX)) {
            numeric = code.substring(PilotFleetSupport.GEO_VEHICLE_PREFIX.length());
        }
        if (numeric == null) {
            return 0;
        }
        try {
            return Integer.parseInt(numeric) - 1;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<VehicleEntity> listPilotVehicles() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0))
                .stream()
                .filter(vehicle -> PilotFleetSupport.isPilotSimVehicleCode(vehicle.getVehicleCode()))
                .filter(this::isSimulationVehicle)
                .sorted(Comparator.comparing(VehicleEntity::getVehicleCode))
                .toList();
    }

    private List<VehicleEntity> listPilotVehicles(String prefix) {
        return listPilotVehicles().stream()
                .filter(vehicle -> vehicle.getVehicleCode() != null && vehicle.getVehicleCode().startsWith(prefix))
                .toList();
    }

    private boolean isSimulationVehicle(VehicleEntity vehicle) {
        String linkMode = vehicle.getLinkMode();
        return linkMode == null || linkMode.isBlank() || VehicleLinkMode.SIM.name().equals(linkMode);
    }
}
