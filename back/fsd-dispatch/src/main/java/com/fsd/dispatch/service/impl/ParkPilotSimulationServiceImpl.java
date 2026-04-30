package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.service.ParkRoutePlannerService;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkPilotSimulationServiceImpl implements ParkPilotSimulationService {

    private static final BigDecimal MIN_BATTERY_LEVEL = new BigDecimal("10");
    private static final int CHARGE_RECOVERY_PER_TICK = 8;
    private static final int CHARGE_COMPLETE_LEVEL = 95;
    private static final String PILOT_VEHICLE_PREFIX = "PARK-";

    private final ParkPilotProperties parkPilotProperties;
    private final VehicleMapper vehicleMapper;
    private final OrderStateService orderStateService;
    private final VehicleReportService vehicleReportService;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final Map<Long, VehicleRuntimeState> runtimeStates = new ConcurrentHashMap<>();

    public ParkPilotSimulationServiceImpl(ParkPilotProperties parkPilotProperties,
                                          VehicleMapper vehicleMapper,
                                          OrderStateService orderStateService,
                                          VehicleReportService vehicleReportService,
                                          ParkRoutePlannerService parkRoutePlannerService) {
        this.parkPilotProperties = parkPilotProperties;
        this.vehicleMapper = vehicleMapper;
        this.orderStateService = orderStateService;
        this.vehicleReportService = vehicleReportService;
        this.parkRoutePlannerService = parkRoutePlannerService;
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
            vehicle.setVehicleName("Pilot Vehicle " + (i + 1));
            vehicle.setVehicleType("AGV");
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
            runtimeStates.put(vehicle.getId(), createIdleState(vehicle, i));
        }
    }

    @Override
    public List<ParkVehicleSnapshotResponse> buildSnapshots(List<VehicleEntity> vehicles) {
        return vehicles.stream()
                .sorted(Comparator.comparing(VehicleEntity::getVehicleCode))
                .map(vehicle -> {
                    VehicleRuntimeState state = runtimeStates.computeIfAbsent(vehicle.getId(),
                            key -> createIdleState(vehicle, extractVehicleIndex(vehicle)));
                    return ParkVehicleSnapshotResponse.builder()
                            .vehicleId(vehicle.getId())
                            .vehicleCode(vehicle.getVehicleCode())
                            .vehicleName(vehicle.getVehicleName())
                            .onlineStatus(vehicle.getOnlineStatus())
                            .dispatchStatus(vehicle.getDispatchStatus())
                            .currentTaskId(vehicle.getCurrentTaskId())
                            .currentOrderId(vehicle.getCurrentOrderId())
                            .batteryLevel(vehicle.getBatteryLevel())
                            .x(vehicle.getCurrentLongitude())
                            .y(vehicle.getCurrentLatitude())
                            .runtimeStage(state.stage)
                            .targetCode(state.targetCode)
                            .targetType(state.targetType)
                            .charging("CHARGING".equals(state.stage))
                            .lowBattery(isLowBattery(vehicle))
                            .trajectory(new ArrayList<>(state.trail))
                            .build();
                })
                .toList();
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${fsd.park.simulation.tick-interval-ms:1000}")
    @Transactional
    public void tick() {
        initializeVehiclesIfNeeded();
        if (!parkPilotProperties.isEnabled() || !parkPilotProperties.getSimulation().isEnabled()) {
            return;
        }
        for (VehicleEntity vehicle : listPilotVehicles()) {
            tickVehicle(vehicle);
        }
    }

    private void tickVehicle(VehicleEntity vehicle) {
        VehicleRuntimeState state = runtimeStates.computeIfAbsent(vehicle.getId(),
                key -> createIdleState(vehicle, extractVehicleIndex(vehicle)));
        if (VehicleDispatchStatus.BUSY.name().equals(vehicle.getDispatchStatus())
                && vehicle.getCurrentTaskId() != null
                && vehicle.getCurrentOrderId() != null) {
            syncBusyState(vehicle, state);
            processBusyVehicle(vehicle, state);
            return;
        }
        if (state.offlineUntil != null && state.offlineUntil.isAfter(LocalDateTime.now())) {
            vehicle.setOnlineStatus(VehicleOnlineStatus.OFFLINE.name());
            vehicle.setLastReportTime(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
            recordPoint(state, vehicle);
            return;
        }
        state.offlineUntil = null;
        processIdleVehicle(vehicle, state);
    }

    private void processIdleVehicle(VehicleEntity vehicle, VehicleRuntimeState state) {
        maybeGoOffline(state);
        vehicle.setOnlineStatus(state.offlineUntil == null ? VehicleOnlineStatus.ONLINE.name() : VehicleOnlineStatus.OFFLINE.name());
        if (state.offlineUntil == null) {
            vehicle.setDispatchStatus(VehicleDispatchStatus.IDLE.name());
            processIdleStage(vehicle, state);
        }
        vehicle.setLastReportTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        recordPoint(state, vehicle);
    }

    private void processBusyVehicle(VehicleEntity vehicle, VehicleRuntimeState state) {
        vehicle.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
        vehicle.setDispatchStatus(VehicleDispatchStatus.BUSY.name());
        switch (state.stage) {
            case "TO_PICKUP" -> {
                moveVehicleAlongRoute(vehicle, state);
                if (state.routeIndex >= state.route.size()) {
                    submitVehicleReport(vehicle, "START_EXECUTE", "Arrived at pickup station");
                    state.stage = "LOADING";
                    state.holdUntil = LocalDateTime.now().plusSeconds(3);
                    rotateDropoffTarget(state);
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
        reduceBattery(vehicle, true);
        vehicle.setLastReportTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        recordPoint(state, vehicle);
    }

    private void syncBusyState(VehicleEntity vehicle, VehicleRuntimeState state) {
        if (!Objects.equals(state.taskId, vehicle.getCurrentTaskId()) || !Objects.equals(state.orderId, vehicle.getCurrentOrderId())) {
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
            state.route = parkRoutePlannerService.buildRoute(vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude(),
                    pickup.getX(), pickup.getY());
            state.routeIndex = 1;
            state.holdUntil = null;
            state.offlineUntil = null;
        }
    }

    private void rotateDropoffTarget(VehicleRuntimeState state) {
        state.targetX = state.nextTargetX;
        state.targetY = state.nextTargetY;
        state.targetCode = state.nextTargetCode;
        state.targetType = state.nextTargetType;
        state.route = parkRoutePlannerService.buildRoute(state.lastX, state.lastY, state.targetX, state.targetY);
        state.routeIndex = 1;
    }

    private void routeAfterDelivery(VehicleEntity vehicle, VehicleRuntimeState state) {
        if (isLowBattery(vehicle)) {
            routeToCharging(state);
            return;
        }
        routeToStandby(state);
    }

    private ParkStationResponse getStation(Long stationId) {
        return parkPilotProperties.getStations().stream()
                .filter(station -> Objects.equals(station.getId(), stationId))
                .findFirst()
                .map(station -> ParkStationResponse.builder()
                        .stationId(station.getId())
                        .stationCode(station.getCode())
                        .stationName(station.getName())
                        .x(station.getX())
                        .y(station.getY())
                        .area(station.getArea())
                        .build())
                .orElseThrow(() -> new BusinessException("PARK_STATION_NOT_FOUND", "Park station not found"));
    }

    private void maybeGoOffline(VehicleRuntimeState state) {
        if (state.offlineUntil == null
                && "STANDBY".equals(state.stage)
                && ThreadLocalRandom.current().nextDouble() < parkPilotProperties.getSimulation().getOfflineProbability()) {
            state.offlineUntil = LocalDateTime.now().plusSeconds(parkPilotProperties.getSimulation().getOfflineDurationSeconds());
            state.stage = "OFFLINE";
        }
    }

    private void processIdleStage(VehicleEntity vehicle, VehicleRuntimeState state) {
        if (isLowBattery(vehicle) && !"TO_CHARGING".equals(state.stage) && !"CHARGING".equals(state.stage)) {
            routeToCharging(state);
        }
        switch (state.stage) {
            case "TO_CHARGING" -> {
                moveVehicleAlongRoute(vehicle, state);
                reduceBattery(vehicle, false);
                if (state.routeIndex >= state.route.size()) {
                    state.stage = "CHARGING";
                    state.route = List.of();
                    state.routeIndex = 0;
                }
            }
            case "CHARGING" -> {
                recoverBattery(vehicle);
                if (!isLowBattery(vehicle) || vehicle.getBatteryLevel() >= CHARGE_COMPLETE_LEVEL) {
                    routeToStandby(state);
                }
            }
            case "RETURNING_TO_STANDBY" -> {
                moveVehicleAlongRoute(vehicle, state);
                reduceBattery(vehicle, false);
                if (state.routeIndex >= state.route.size()) {
                    state.stage = "STANDBY";
                    state.route = List.of();
                    state.routeIndex = 0;
                }
            }
            case "STANDBY" -> {
                ensureStandbyLocation(state);
                if (state.route != null && state.routeIndex < state.route.size()) {
                    moveVehicleAlongRoute(vehicle, state);
                }
                reduceBattery(vehicle, false);
            }
            default -> {
                routeToStandby(state);
                processIdleStage(vehicle, state);
            }
        }
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

    private void moveVehicleAlongRoute(VehicleEntity vehicle, VehicleRuntimeState state) {
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

    private void reduceBattery(VehicleEntity vehicle, boolean busy) {
        int current = vehicle.getBatteryLevel() == null ? 100 : vehicle.getBatteryLevel();
        int drain = busy ? 1 : 0;
        if (busy || ThreadLocalRandom.current().nextDouble() < 0.2D) {
            current = Math.max(MIN_BATTERY_LEVEL.intValue(), current - drain - (busy ? 0 : 1));
        }
        vehicle.setBatteryLevel(current);
    }

    private void recoverBattery(VehicleEntity vehicle) {
        int current = vehicle.getBatteryLevel() == null ? 100 : vehicle.getBatteryLevel();
        vehicle.setBatteryLevel(Math.min(100, current + CHARGE_RECOVERY_PER_TICK));
    }

    private boolean isLowBattery(VehicleEntity vehicle) {
        int batteryLevel = vehicle.getBatteryLevel() == null ? 100 : vehicle.getBatteryLevel();
        return batteryLevel <= parkPilotProperties.getSimulation().getLowBatteryThreshold();
    }

    private boolean isNear(BigDecimal currentX, BigDecimal currentY, BigDecimal targetX, BigDecimal targetY) {
        if (currentX == null || currentY == null || targetX == null || targetY == null) {
            return false;
        }
        return Math.hypot(currentX.doubleValue() - targetX.doubleValue(),
                currentY.doubleValue() - targetY.doubleValue()) <= parkPilotProperties.getVehicleSpeedPxPerSecond().doubleValue();
    }

    private void recordPoint(VehicleRuntimeState state, VehicleEntity vehicle) {
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

    private VehicleRuntimeState createIdleState(VehicleEntity vehicle, int index) {
        VehicleRuntimeState state = new VehicleRuntimeState();
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

    private void routeToStandby(VehicleRuntimeState state) {
        ensureStandbyLocation(state);
        state.stage = "RETURNING_TO_STANDBY";
        routeToTarget(state, state.standbyPoint, "STANDBY");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "STANDBY";
        }
    }

    private void routeToCharging(VehicleRuntimeState state) {
        ensureStandbyLocation(state);
        state.stage = "TO_CHARGING";
        routeToTarget(state, state.chargingPoint, "CHARGING");
        if (state.routeIndex >= state.route.size()) {
            state.stage = "CHARGING";
        }
    }

    private void ensureStandbyLocation(VehicleRuntimeState state) {
        if (state.standbyPoint == null) {
            state.standbyPoint = getStandbySpot(0);
        }
        if (state.chargingPoint == null) {
            state.chargingPoint = getChargingSpot(0);
        }
    }

    private void routeToTarget(VehicleRuntimeState state, ParkPointResponse point, String targetType) {
        state.targetCode = point.getCode();
        state.targetType = targetType;
        state.targetX = point.getX();
        state.targetY = point.getY();
        if (state.lastX == null || state.lastY == null) {
            state.route = List.of(point);
            state.routeIndex = 0;
            return;
        }
        state.route = parkRoutePlannerService.buildRoute(state.lastX, state.lastY, point.getX(), point.getY());
        state.routeIndex = state.route.size() > 1 ? 1 : state.route.size();
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
                .sorted(Comparator.comparing(VehicleEntity::getVehicleCode))
                .toList();
    }

    private static class VehicleRuntimeState {
        private String stage;
        private Long taskId;
        private Long orderId;
        private BigDecimal targetX;
        private BigDecimal targetY;
        private String targetCode;
        private String targetType;
        private BigDecimal nextTargetX;
        private BigDecimal nextTargetY;
        private String nextTargetCode;
        private String nextTargetType;
        private BigDecimal lastX;
        private BigDecimal lastY;
        private ParkPointResponse standbyPoint;
        private ParkPointResponse chargingPoint;
        private LocalDateTime holdUntil;
        private LocalDateTime offlineUntil;
        private List<ParkPointResponse> route = List.of();
        private int routeIndex;
        private final Deque<ParkPointResponse> trail = new ArrayDeque<>();
    }
}
