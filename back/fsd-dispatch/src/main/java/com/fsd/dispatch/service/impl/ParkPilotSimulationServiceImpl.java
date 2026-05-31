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
import com.fsd.dispatch.fleet.simulation.SimulationFleetAdapter;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.fleet.simulation.SimulationMotionStore;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkingFacilityService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkPilotSimulationServiceImpl implements ParkPilotSimulationService {

    private static final String PILOT_VEHICLE_PREFIX = "PARK-";
    private static final List<String> DISPATCH_DEMAND_STATUSES = List.of(
            DispatchTaskStatus.PENDING.name(),
            DispatchTaskStatus.ASSIGNING.name(),
            DispatchTaskStatus.MANUAL_PENDING.name(),
            DispatchTaskStatus.ASSIGNED.name(),
            DispatchTaskStatus.EXECUTING.name());

    private final ParkPilotProperties parkPilotProperties;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final FleetChargePolicy fleetChargePolicy;
    private final VehicleMapper vehicleMapper;
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
    private boolean dispatchDemandActive;

    public ParkPilotSimulationServiceImpl(ParkPilotProperties parkPilotProperties,
                                          FleetEnergyProperties fleetEnergyProperties,
                                          FleetChargePolicy fleetChargePolicy,
                                          VehicleMapper vehicleMapper,
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
                                          MapfRoutePlannerService mapfRoutePlannerService) {
        this.parkPilotProperties = parkPilotProperties;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.fleetChargePolicy = fleetChargePolicy;
        this.vehicleMapper = vehicleMapper;
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
    }

    @Override
    @Transactional
    public void initializeVehiclesIfNeeded() {
        if (!parkPilotProperties.isEnabled() || !parkPilotProperties.getSimulation().isEnabled()) {
            return;
        }
        List<VehicleEntity> pilotVehicles = listPilotVehicles();
        int targetCount = parkPilotProperties.getSimulation().getVehicleCount();
        for (int i = pilotVehicles.size(); i < targetCount; i++) {
            ParkPointResponse parkingSpot = getStandbySpot(i);
            VehicleEntity vehicle = new VehicleEntity();
            vehicle.setVehicleCode(PILOT_VEHICLE_PREFIX + String.format("%02d", i + 1));
            vehicle.setVehicleName("家纺无人快递车 " + (i + 1));
            vehicle.setVehicleType("L4_DELIVERY");
            vehicle.setLinkMode(VehicleLinkMode.SIM.name());
            vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
            vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
            vehicle.setCurrentLongitude(parkingSpot.getX());
            vehicle.setCurrentLatitude(parkingSpot.getY());
            vehicle.setBatteryLevel(ThreadLocalRandom.current().nextInt(80, 101));
            vehicle.setLastReportTime(LocalDateTime.now());
            vehicle.setRemark("park-pilot");
            vehicle.setVersion(0);
            vehicle.setDeleted(0);
            vehicleMapper.insert(vehicle);
            simulationMotionStore.put(vehicle.getId(), createIdleState(vehicle, i));
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
    }

    private void tickVehicle(VehicleEntity vehicle) {
        SimulationMotionState state = simulationMotionStore.getOrCreate(vehicle.getId(),
                () -> createIdleState(vehicle, extractVehicleIndex(vehicle)));
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
                    rotateDropoffTarget(state, vehicle.getId());
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
                || "RETURNING_TO_STANDBY".equals(stage);
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
        state.holdUntil = null;
        state.offlineUntil = null;
        state.busyMoveTicks = 0;
        state.pluggedIn = false;
        parkingFacilityService.releaseByVehicle(vehicle.getId());
    }

    private void rotateDropoffTarget(SimulationMotionState state, Long vehicleId) {
        state.targetX = state.nextTargetX;
        state.targetY = state.nextTargetY;
        state.targetCode = state.nextTargetCode;
        state.targetType = state.nextTargetType;
        state.route = planRoute(vehicleId, state.lastX, state.lastY, state.targetX, state.targetY);
        state.routeIndex = 1;
    }

    private void routeAfterDelivery(VehicleEntity vehicle, SimulationMotionState state) {
        if (isLowBattery(vehicle)) {
            routeToEnergyRecovery(vehicle, state);
            return;
        }
        if (shouldPreferCharging()) {
            if (isFullyCharged(vehicle)) {
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
                if (isFullyCharged(vehicle)) {
                    enterPluggedInStandby(vehicle, state);
                }
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
                if (shouldPreferCharging() && !isFullyCharged(vehicle)) {
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
                ensureStandbyLocation(state);
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
        ensureStandbyLocation(state);
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
        routeToTarget(vehicle.getId(), state, state.swapPoint, "SWAP");
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
        if (isLowBattery(vehicle)) {
            return true;
        }
        if (!shouldPreferCharging()) {
            return false;
        }
        return !isFullyCharged(vehicle) || !state.pluggedIn;
    }

    private boolean isPluggedInStandby(VehicleEntity vehicle, SimulationMotionState state) {
        return state.pluggedIn && isFullyCharged(vehicle) && "STANDBY".equals(state.stage);
    }

    private void enterPluggedInStandby(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(state);
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
        ensureStandbyLocation(state);
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

    private boolean isLowBattery(VehicleEntity vehicle) {
        return fleetChargePolicy.isLowSoc(vehicle.getBatteryLevel());
    }

    private boolean isFullyCharged(VehicleEntity vehicle) {
        return fleetChargePolicy.isFullyCharged(vehicle.getBatteryLevel());
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

    private void reduceBattery(VehicleEntity vehicle, boolean busy, SimulationMotionState state) {
        if (isChargingStage(state.stage) || isPluggedInStandby(vehicle, state)) {
            return;
        }
        int current = normalizeBattery(vehicle.getBatteryLevel());
        int floor = fleetEnergyProperties.getReserveSocFloor();
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
        state.trail.addLast(ParkPointResponse.builder()
                .code(null)
                .x(vehicle.getCurrentLongitude())
                .y(vehicle.getCurrentLatitude())
                .build());
        while (state.trail.size() > parkPilotProperties.getSimulation().getMaxTrailSize()) {
            state.trail.removeFirst();
        }
    }

    private SimulationMotionState createIdleState(VehicleEntity vehicle, int index) {
        SimulationMotionState state = new SimulationMotionState();
        state.standbyPoint = getStandbySpot(index);
        state.chargingPoint = getChargingSpot(index);
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

    private ParkPointResponse getChargingSpot(int index) {
        return getStandbySpot(index);
    }

    private void routeToStandby(VehicleEntity vehicle, SimulationMotionState state) {
        if (shouldPreferCharging() && !state.pluggedIn) {
            routeToCharging(vehicle, state);
            return;
        }
        ensureStandbyLocation(state);
        state.pluggedIn = false;
        state.stage = "RETURNING_TO_STANDBY";
        routeToTarget(vehicle.getId(), state, state.standbyPoint, "STANDBY");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "STANDBY";
        }
    }

    private void routeToCharging(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(state);
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
        routeToTarget(vehicle.getId(), state, state.chargingPoint, "CHARGING");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "CHARGING";
            bindCharging(vehicle, state);
        }
    }

    private void ensureStandbyLocation(SimulationMotionState state) {
        if (state.standbyPoint == null) {
            state.standbyPoint = getStandbySpot(0);
        }
        if (state.chargingPoint == null) {
            state.chargingPoint = getChargingSpot(0);
        }
    }

    private void routeToTarget(Long vehicleId, SimulationMotionState state, ParkPointResponse point, String targetType) {
        state.targetCode = point.getCode();
        state.targetType = targetType;
        state.targetX = point.getX();
        state.targetY = point.getY();
        if (state.lastX == null || state.lastY == null) {
            state.route = List.of(point);
            state.routeIndex = 0;
            return;
        }
        state.route = planRoute(vehicleId, state.lastX, state.lastY, point.getX(), point.getY());
        state.routeIndex = state.route.size() > 1 ? 1 : state.route.size();
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
        ensureStandbyLocation(state);
        if (state.chargingPoint == null || state.chargingPoint.getCode() == null) {
            return;
        }
        parkingFacilityService.markCharging(defaultParkId(), vehicle.getId(), state.chargingPoint.getCode());
    }

    private void bindPluggedStandby(VehicleEntity vehicle, SimulationMotionState state) {
        ensureStandbyLocation(state);
        if (state.chargingPoint == null || state.chargingPoint.getCode() == null) {
            return;
        }
        parkingFacilityService.occupyPluggedStandby(defaultParkId(), vehicle.getId(), state.chargingPoint.getCode());
    }

    private int extractVehicleIndex(VehicleEntity vehicle) {
        String code = vehicle.getVehicleCode();
        if (code == null || !code.startsWith(PILOT_VEHICLE_PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(code.substring(PILOT_VEHICLE_PREFIX.length())) - 1;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<VehicleEntity> listPilotVehicles() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0))
                .stream()
                .filter(vehicle -> vehicle.getVehicleCode() != null && vehicle.getVehicleCode().startsWith(PILOT_VEHICLE_PREFIX))
                .filter(this::isSimulationVehicle)
                .sorted(Comparator.comparing(VehicleEntity::getVehicleCode))
                .toList();
    }

    private boolean isSimulationVehicle(VehicleEntity vehicle) {
        String linkMode = vehicle.getLinkMode();
        return linkMode == null || linkMode.isBlank() || VehicleLinkMode.SIM.name().equals(linkMode);
    }
}
