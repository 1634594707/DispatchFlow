package com.fsd.dispatch.dispatch;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.DecisionOutcome;
import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.core.DecisionWeights;
import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.dispatch.core.RulePolicy;
import com.fsd.dispatch.fleet.PilotFleetSupport;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.TelemetryFreshnessPolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.DispatchAutomationRuleService;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.dispatch.service.HubCapacityService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.dispatch.service.TrafficZoneControlService;
import com.fsd.dispatch.geo.DispatchGeoDistanceService;
import com.fsd.dispatch.mapf.MapfRoutePlanResult;
import com.fsd.dispatch.mapf.MapfRoutePlannerService;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchRouteEntity;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class DispatchVehicleAssignServiceImpl implements DispatchVehicleAssignService {

    private final VehicleService vehicleService;
    private final ParkStationService parkStationService;
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final DispatchStrategyRuntimeService strategyRuntimeService;
    private final FleetRuntimeService fleetRuntimeService;
    private final TrafficZoneControlService trafficZoneControlService;
    private final DispatchPauseControlService dispatchPauseControlService;
    private final HubCapacityService hubCapacityService;
    private final DispatchRouteService dispatchRouteService;
    private final PeakModeService peakModeService;
    private final DispatchAutomationRuleService automationRuleService;
    private final DispatchGeoDistanceService dispatchGeoDistanceService;
    private final MapfRoutePlannerService mapfRoutePlannerService;
    private final com.fsd.dispatch.service.ChargingSessionService chargingSessionService;
    private final TelemetryFreshnessPolicy telemetryFreshnessPolicy;
    private final com.fsd.dispatch.service.DispatchDecisionSnapshotService decisionSnapshotService;
    private final DecisionPolicy decisionPolicy;

    public DispatchVehicleAssignServiceImpl(VehicleService vehicleService,
                                            ParkStationService parkStationService,
                                            ParkRoutePlannerService parkRoutePlannerService,
                                            DispatchStrategyRuntimeService strategyRuntimeService,
                                            FleetRuntimeService fleetRuntimeService,
                                            TrafficZoneControlService trafficZoneControlService,
                                            DispatchPauseControlService dispatchPauseControlService,
                                            HubCapacityService hubCapacityService,
                                            DispatchRouteService dispatchRouteService,
                                            PeakModeService peakModeService,
                                            DispatchAutomationRuleService automationRuleService,
                                            DispatchGeoDistanceService dispatchGeoDistanceService,
                                            MapfRoutePlannerService mapfRoutePlannerService,
                                            com.fsd.dispatch.service.ChargingSessionService chargingSessionService,
                                            TelemetryFreshnessPolicy telemetryFreshnessPolicy,
                                            com.fsd.dispatch.service.DispatchDecisionSnapshotService decisionSnapshotService,
                                            DecisionPolicy decisionPolicy) {
        this.vehicleService = vehicleService;
        this.parkStationService = parkStationService;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.strategyRuntimeService = strategyRuntimeService;
        this.fleetRuntimeService = fleetRuntimeService;
        this.trafficZoneControlService = trafficZoneControlService;
        this.dispatchPauseControlService = dispatchPauseControlService;
        this.hubCapacityService = hubCapacityService;
        this.dispatchRouteService = dispatchRouteService;
        this.peakModeService = peakModeService;
        this.automationRuleService = automationRuleService;
        this.dispatchGeoDistanceService = dispatchGeoDistanceService;
        this.mapfRoutePlannerService = mapfRoutePlannerService;
        this.chargingSessionService = chargingSessionService;
        this.telemetryFreshnessPolicy = telemetryFreshnessPolicy;
        this.decisionSnapshotService = decisionSnapshotService;
        this.decisionPolicy = decisionPolicy;
    }

    @Override
    public DispatchAssignResult selectBestVehicle(OrderEntity order) {
        long startedAtNanos = System.nanoTime();
        DecisionTrace trace = new DecisionTrace();
        DispatchAssignResult result = assignWithTrace(order, trace);
        decisionSnapshotService.record(order, trace.getParkId(), trace, result,
                (System.nanoTime() - startedAtNanos) / 1_000L);
        return result;
    }

    private DispatchAssignResult assignWithTrace(OrderEntity order, DecisionTrace trace) {
        Long parkId = resolveParkId(order);
        trace.setParkId(parkId);
        if (dispatchPauseControlService.isDispatchPaused(parkId)) {
            throw new BusinessException("DISPATCH_PAUSED", "当前园区已暂停新派单");
        }
        // 一单只解析一次策略：能量阈值与打分权重必须来自同一侧，否则灰度会混档（§7.2）
        DispatchStrategyRuntimeService.AssignStrategy strategy =
                strategyRuntimeService.strategyForAssign(parkId, strategyBucketKey(order));
        FleetEnergyProperties energy = strategy.energy();
        DispatchScoringProperties scoring = strategy.scoring();
        trace.setProfileId(strategy.profileId());
        trace.setProfileType(strategy.profileType());
        trace.setGrayPercent(strategy.grayPercent());
        trace.setGrayBucket(strategy.bucket());
        trace.setProductionSide(strategy.productionSide());
        trace.setRoadGraphVersion(parkRoutePlannerService.graphVersion(parkId));
        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
        parkStationService.assertStationInPark(order.getPickupPointId(), parkId);
        ParkStationResponse dropoff = parkStationService.requireStation(order.getDropoffPointId());

        // 配送区域只有一个语义：地理派单（示意模式已随 §7.6 删除）
        if (order.getDeliveryZone() == null || order.getDeliveryZone().isBlank()) {
            order.setDeliveryZone("GEO_DELIVERY");
        }

        if (hubCapacityService.isHubLikeStation(pickup) && !hubCapacityService.isHubCapacityAvailable(pickup.getStationId())) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.HUB_CAPACITY_FULL,
                    "Pickup hub/buffer capacity full: " + pickup.getStationName());
        }
        if (hubCapacityService.isHubLikeStation(dropoff) && !hubCapacityService.isHubCapacityAvailable(dropoff.getStationId())) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.HUB_CAPACITY_FULL,
                    "Dropoff hub/mothership capacity full: " + dropoff.getStationName());
        }

        if (order.getRouteId() != null) {
            DispatchRouteEntity route = dispatchRouteService.findRoute(order.getRouteId()).orElse(null);
            if (route != null) {
                if (!dispatchRouteService.isRouteWithinServiceWindow(route)) {
                    return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                            "Route outside service window: " + route.getRouteName());
                }
                if (!dispatchRouteService.isRouteOccupancyAvailable(route)) {
                    return DispatchAssignResult.failure(DispatchAssignFailReason.ROUTE_OCCUPANCY_FULL,
                            "Route concurrent task limit reached: " + route.getRouteName());
                }
            }
        }

        if (trafficZoneControlService.isPointInPausedZone(parkId, pickup.getX(), pickup.getY())) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "Pickup station is inside a traffic pause zone; dispatch suspended for this area");
        }

        List<VehicleEntity> idleOnline = vehicleService.listAssignableVehicles();
        trace.setCandidateTotal(idleOnline.size());
        if (idleOnline.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.NO_VEHICLE,
                    "No online idle vehicle available in fleet");
        }

        // 遥测新鲜度门禁（路线图 5.1）：数据年龄超过统一阈值或从未上报的车辆禁止派车
        List<VehicleEntity> freshTelemetry = idleOnline.stream()
                .filter(vehicle -> !telemetryFreshnessPolicy.isStale(vehicle.getLastReportTime()))
                .toList();
        trace.setFreshTelemetry(freshTelemetry.size());
        if (freshTelemetry.isEmpty() && !idleOnline.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.TELEMETRY_STALE,
                    "All idle vehicles have stale telemetry beyond threshold "
                            + telemetryFreshnessPolicy.threshold().toSeconds() + "s");
        }

        List<VehicleEntity> socEligible = freshTelemetry.stream()
                .filter(vehicle -> normalizeSoc(vehicle.getBatteryLevel()) >= energy.getMinAssignableSoc())
                .toList();
        trace.setSocEligible(socEligible.size());
        if (socEligible.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.LOW_SOC,
                    "All idle vehicles are below minimum assignable SOC");
        }

        // §7.2：这五个约束过滤器原本和 SOC 挤在同一层，任何一条不满足都对外报 LOW_SOC ⇒
        // "派单失败原因分布"里的低电量占比其实混进了车型/车队池/配送区/载重/维保。拆开后各报各的；
        // 筛选与诊断共用同一张过滤器表（下面的 survivors 就是逐层存活数），所以消息不会和实际判定漂移。
        List<VehicleEntity> constraintEligible = socEligible;
        Map<String, Integer> survivors = new LinkedHashMap<>();
        String binding = "COMBINATION";
        for (Map.Entry<String, Predicate<VehicleEntity>> filter : orderConstraintFilters(order).entrySet()) {
            constraintEligible = constraintEligible.stream().filter(filter.getValue()).toList();
            survivors.put(filter.getKey(), constraintEligible.size());
            if (constraintEligible.isEmpty()) {
                binding = filter.getKey();
                break;
            }
        }
        if (constraintEligible.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.NO_MATCHING_VEHICLE,
                    "No idle vehicle satisfies the order constraints (SOC-passing candidates: "
                            + socEligible.size() + "; survivors per filter " + survivors
                            + "; binding: " + binding + ")");
        }

        // 全链路SOC校验：取货+送货+返航充电站后SOC需 > 安全余量
        List<VehicleEntity> socChainEligible = constraintEligible.stream()
                .filter(vehicle -> canCompleteTaskWithSoc(parkId, vehicle, pickup, dropoff, energy))
                .toList();
        trace.setSocChainEligible(socChainEligible.size());
        if (socChainEligible.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.LOW_SOC,
                    "All idle vehicles cannot complete the full task chain with safe SOC margin");
        }

        List<VehicleEntity> reachableVehicles = new ArrayList<>();
        List<Double> parkDistances = new ArrayList<>();
        for (VehicleEntity vehicle : socChainEligible) {
            double distance = estimateRouteDistance(parkId, vehicle, pickup);
            if (Double.isInfinite(distance)) {
                continue;
            }
            reachableVehicles.add(vehicle);
            parkDistances.add(distance);
        }
        trace.setReachable(reachableVehicles.size());
        if (reachableVehicles.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "Pickup station is not reachable from any candidate vehicle on the road network");
        }

        List<Double> blendedDistances = dispatchGeoDistanceService.applyGeoBlend(reachableVehicles, pickup, parkDistances);
        Map<Long, VehicleEntity> vehiclesById = indexById(reachableVehicles);
        DecisionOutcome outcome = decisionPolicy.decide(new DecisionInput(
                order.getPriority(),
                peakModeService.isPeakMode(parkId),
                automationRuleService.resolvePeakDistanceFactor(parkId, 0.85D),
                toWeights(energy, scoring),
                toCandidateStates(reachableVehicles, blendedDistances, energy)));
        List<RankedCandidate> reachable = outcome.ranked();
        trace.setRanked(reachable);
        trace.setPolicyId(outcome.policyId());
        trace.setPolicyVersion(outcome.policyVersion());
        RankedCandidate best = selectWithMapfReservation(parkId, pickup, reachable, vehiclesById);
        if (best == null) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "No conflict-free MAPF route to pickup from any candidate vehicle");
        }
        String explanation = RulePolicy.explain(best,
                dispatchGeoDistanceService.isGeoBlendEnabled(),
                mapfRoutePlannerService.isEnabled());
        return DispatchAssignResult.success(vehiclesById.get(best.vehicleId()), explanation, best.totalScore(),
                best.distanceScore(), best.socScore(), best.pluggedBonus());
    }

    private static Map<Long, VehicleEntity> indexById(List<VehicleEntity> vehicles) {
        Map<Long, VehicleEntity> byId = new LinkedHashMap<>(vehicles.size());
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.getId() != null) {
                byId.put(vehicle.getId(), vehicle);
            }
        }
        return byId;
    }

    private DecisionWeights toWeights(FleetEnergyProperties energy, DispatchScoringProperties scoring) {
        return new DecisionWeights(
                scoring.getWeightDistance(),
                scoring.getWeightSocMargin(),
                scoring.getWeightPluggedStandbyBonus(),
                scoring.getWeightFairness(),
                scoring.getMaxIdleBonus(),
                energy.getFullSoc(),
                DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR,
                DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
                DecisionWeights.DEFAULT_PEAK_SOC_DAMPING,
                DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);
    }

    private List<DecisionInput.CandidateState> toCandidateStates(List<VehicleEntity> vehicles,
                                                                 List<Double> distances,
                                                                 FleetEnergyProperties energy) {
        List<DecisionInput.CandidateState> states = new ArrayList<>(vehicles.size());
        for (int i = 0; i < vehicles.size(); i++) {
            VehicleEntity vehicle = vehicles.get(i);
            int soc = normalizeSoc(vehicle.getBatteryLevel());
            states.add(new DecisionInput.CandidateState(
                    new DecisionInput.RankedCandidateIdentity(vehicle.getId(), vehicle.getVehicleCode()),
                    soc,
                    distances.get(i),
                    isPluggedFullStandby(vehicle, soc, energy),
                    idleMinutes(vehicle)));
        }
        return states;
    }

    private boolean isPluggedFullStandby(VehicleEntity vehicle, int soc, FleetEnergyProperties energy) {
        Optional<FleetRuntime> runtime = fleetRuntimeService.get(vehicle.getId());
        return runtime.isPresent()
                && Boolean.TRUE.equals(runtime.get().getPluggedIn())
                && "STANDBY".equals(runtime.get().getRuntimeStage())
                && soc == energy.getFullSoc();
    }

    private static long idleMinutes(VehicleEntity vehicle) {
        if (vehicle.getLastReportTime() == null) {
            return 0L;
        }
        return java.time.Duration.between(vehicle.getLastReportTime(), java.time.LocalDateTime.now()).toMinutes();
    }

    private RankedCandidate selectWithMapfReservation(Long parkId, ParkStationResponse pickup,
                                                      List<RankedCandidate> ranked,
                                                      Map<Long, VehicleEntity> vehiclesById) {
        if (!mapfRoutePlannerService.isEnabled()) {
            return ranked.get(0);
        }
        for (RankedCandidate candidate : ranked) {
            VehicleEntity vehicle = vehiclesById.get(candidate.vehicleId());
            MapfRoutePlanResult plan = mapfRoutePlannerService.planAndReserve(
                    parkId,
                    vehicle.getId(),
                    vehicle.getCurrentLongitude(),
                    vehicle.getCurrentLatitude(),
                    pickup.getX(),
                    pickup.getY());
            if (plan.isSuccess() && plan.isReserved()) {
                return candidate;
            }
        }
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** 灰度分桶键：稳定、可复现，同一单重放必须落进同一侧。 */
    private String strategyBucketKey(OrderEntity order) {
        return "order:" + (order.getId() != null ? order.getId() : order.getOrderNo());
    }

    private Long resolveParkId(OrderEntity order) {
        if (order.getParkId() != null) {
            return order.getParkId();
        }
        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
        return pickup.getParkId() != null ? pickup.getParkId() : parkStationService.requireDefaultPark().getId();
    }

    private double estimateRouteDistance(Long parkId, VehicleEntity vehicle, ParkStationResponse station) {
        BigDecimal currentX = vehicle.getCurrentLongitude();
        BigDecimal currentY = vehicle.getCurrentLatitude();
        if (currentX == null || currentY == null) {
            return Double.MAX_VALUE;
        }
        if (!parkRoutePlannerService.isReachable(parkId, currentX, currentY, station.getX(), station.getY())) {
            return Double.POSITIVE_INFINITY;
        }
        List<ParkPointResponse> route = parkRoutePlannerService.buildRoute(
                parkId, currentX, currentY, station.getX(), station.getY());
        // Phase 4：补全 START/END 的 GPS 坐标，使 pathLength 全程使用 haversine（米）。
        // 车辆 currentLongitude/currentLatitude 实际存储 schematic x/y，需通过
        // DispatchGeoDistanceService 解析为真实 GCJ-02；站点直接取 coordLng/coordLat。
        enrichRouteEndpointsGeo(route, vehicle, station);
        return pathLength(route);
    }

    private double estimateRouteDistance(Long parkId, ParkStationResponse from, ParkStationResponse to) {
        if (!parkRoutePlannerService.isReachable(parkId, from.getX(), from.getY(), to.getX(), to.getY())) {
            return Double.POSITIVE_INFINITY;
        }
        List<ParkPointResponse> route = parkRoutePlannerService.buildRoute(
                parkId, from.getX(), from.getY(), to.getX(), to.getY());
        // Phase 4：站点间路径同样补全 GPS 端点，pathLength 返回真实路网距离（米）。
        enrichRouteEndpointsGeo(route, from, to);
        return pathLength(route);
    }

    /**
     * Phase 4：为路径的 START/END 端点补全 GPS 坐标。中间节点已由 buildRouteFromNodePath
     * 携带 coordLng/coordLat。补全后 pathLength 可全程使用 haversine，避免米与像素混用。
     * GPS 解析为尽力而为：失败时回退到 schematic 欧几里得（仅 START→node 段受影响）。
     */
    private void enrichRouteEndpointsGeo(List<ParkPointResponse> route, VehicleEntity vehicle,
                                         ParkStationResponse station) {
        if (route == null || route.isEmpty()) {
            return;
        }
        ParkPointResponse start = route.get(0);
        if (start.getLongitude() == null) {
            try {
                dispatchGeoDistanceService.resolveVehicleGeo(vehicle).ifPresent(geo -> {
                    start.setLongitude(geo.longitude());
                    start.setLatitude(geo.latitude());
                });
            } catch (RuntimeException ex) {
                // GPS 解析依赖未就绪（如 fleetGeoResolver 未配置）时回退到 schematic
            }
        }
        enrichEnd(route, station.getCoordLng(), station.getCoordLat());
    }

    private void enrichRouteEndpointsGeo(List<ParkPointResponse> route,
                                         ParkStationResponse from, ParkStationResponse to) {
        if (route == null || route.isEmpty()) {
            return;
        }
        ParkPointResponse start = route.get(0);
        if (start.getLongitude() == null && from.getCoordLng() != null && from.getCoordLat() != null) {
            start.setLongitude(from.getCoordLng());
            start.setLatitude(from.getCoordLat());
        }
        enrichEnd(route, to.getCoordLng(), to.getCoordLat());
    }

    private void enrichEnd(List<ParkPointResponse> route, BigDecimal endLng, BigDecimal endLat) {
        int last = route.size() - 1;
        ParkPointResponse end = route.get(last);
        if (end.getLongitude() == null && endLng != null && endLat != null) {
            end.setLongitude(endLng);
            end.setLatitude(endLat);
        }
    }

    private boolean canCompleteTaskWithSoc(Long parkId, VehicleEntity vehicle,
                                            ParkStationResponse pickup, ParkStationResponse dropoff,
                                            FleetEnergyProperties energy) {
        int soc = normalizeSoc(vehicle.getBatteryLevel());
        double pickupDist = estimateRouteDistance(parkId, vehicle, pickup);
        // If the pickup is unreachable, skip the SOC chain check — the downstream
        // reachability scan will produce the correct UNREACHABLE failure reason
        // rather than masking it as LOW_SOC.
        if (Double.isInfinite(pickupDist)) {
            return true;
        }
        double dropoffDist = estimateRouteDistance(parkId, pickup, dropoff);
        if (Double.isInfinite(dropoffDist)) {
            return true;
        }
        // ALG-04 fix: include the distance from the dropoff station back to the nearest
        // charging pile. Without this, a vehicle could complete pickup+dropoff but then
        // run out of charge before reaching a charger, leaving it stranded.
        double returnToChargerMeters = chargingSessionService.estimateDistanceToNearestChargingPile(
                parkId, dropoff.getCoordLng(), dropoff.getCoordLat());
        double returnDist = returnToChargerMeters;
        if (Double.isInfinite(returnDist) || returnDist >= Double.MAX_VALUE / 2) {
            // No charging piles configured: fall back to a fixed 200m reserve estimate
            // so we don't reject all assignments in environments without chargers.
            returnDist = 200D;
        }
        // Phase 4：距离已统一为米（pathLength 使用 haversine）。使用 busyDrainMetersPerPercent
        // （每 1% SOC 可行驶米数）计算耗电，与 ChargingSessionServiceImpl/Simulation 保持一致。
        // 历史硬编码 0.05 系 schematic 像素时代的值，切换到米后会导致 SOC 估算严重偏高。
        double drainRate = 1.0D / Math.max(50D, energy.getBusyDrainMetersPerPercent());
        int consumedSoc = (int) Math.ceil((pickupDist + dropoffDist + returnDist) * drainRate);
        return soc - consumedSoc >= energy.getMinAssignableSoc();
    }


    private int normalizeSoc(Integer batteryLevel) {
        return batteryLevel == null ? 100 : batteryLevel;
    }

    /**
     * SOC 之外的派单约束，**同一张表既用来筛车、也用来报"是哪一层把候选清零"**（§7.2）。
     * 顺序即诊断顺序：维保 → 车型 → 车队池 → 配送区 → 载重。新增约束只改这里，
     * 不要再往调用点塞 filter —— 一旦两处不一致，失败原因就会又开始骗人。
     */
    private Map<String, Predicate<VehicleEntity>> orderConstraintFilters(OrderEntity order) {
        Map<String, Predicate<VehicleEntity>> filters = new LinkedHashMap<>();
        filters.put("MAINTENANCE", vehicle -> !isUnderMaintenance(vehicle));
        filters.put("VEHICLE_TYPE", vehicle -> matchesRequiredVehicleType(order, vehicle));
        filters.put("FLEET_POOL", PilotFleetSupport::matchesOrderFleet);
        filters.put("DELIVERY_ZONE", vehicle -> matchesDeliveryZone(order, vehicle));
        filters.put("LOAD_CAPACITY", vehicle -> matchesLoadCapacity(order, vehicle));
        return filters;
    }

    private boolean isUnderMaintenance(VehicleEntity vehicle) {
        // listAssignableVehicles 已按 IDLE 过滤，此处对 UNAVAILABLE（维保中）再做一次防御性过滤
        return "UNAVAILABLE".equals(vehicle.getDispatchStatus());
    }

    private boolean matchesRequiredVehicleType(OrderEntity order, VehicleEntity vehicle) {
        if (order.getRouteId() == null) {
            return true;
        }
        return dispatchRouteService.findRoute(order.getRouteId())
                .map(DispatchRouteEntity::getRequiredVehicleType)
                .map(required -> required == null || required.isBlank()
                        || required.equalsIgnoreCase(vehicle.getVehicleType())
                        || "GENERAL".equalsIgnoreCase(vehicle.getVehicleType()))
                .orElse(true);
    }

    private boolean matchesDeliveryZone(OrderEntity order, VehicleEntity vehicle) {
        String vehicleZone = vehicle.getDeliveryZone();
        // 车辆未配置区域或为BOTH：匹配所有订单
        if (vehicleZone == null || vehicleZone.isBlank() || "BOTH".equals(vehicleZone)) {
            return true;
        }
        // 根据取货站点判断订单区域
        String orderZone = order.getDeliveryZone();
        if (orderZone == null || orderZone.isBlank()) {
            orderZone = "GEO_DELIVERY";
        }
        return vehicleZone.equals(orderZone);
    }

    private boolean matchesLoadCapacity(OrderEntity order, VehicleEntity vehicle) {
        Integer capacity = vehicle.getMaxLoadCapacity();
        if (capacity == null || capacity <= 0) {
            return true; // 未配置载重则跳过
        }
        if (order.getWeight() == null || order.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return capacity >= order.getWeight().intValue();
    }

    private double pathLength(List<ParkPointResponse> route) {
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
}
