package com.fsd.dispatch.dispatch;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
                                            MapfRoutePlannerService mapfRoutePlannerService) {
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
    }

    @Override
    public DispatchAssignResult selectBestVehicle(OrderEntity order) {
        Long parkId = resolveParkId(order);
        if (dispatchPauseControlService.isDispatchPaused(parkId)) {
            throw new BusinessException("DISPATCH_PAUSED", "当前园区已暂停新派单");
        }
        FleetEnergyProperties energy = strategyRuntimeService.energyForAssign(parkId);
        DispatchScoringProperties scoring = strategyRuntimeService.scoringForAssign(parkId);
        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
        parkStationService.assertStationInPark(order.getPickupPointId(), parkId);
        ParkStationResponse dropoff = parkStationService.requireStation(order.getDropoffPointId());

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
        if (idleOnline.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.NO_VEHICLE,
                    "No online idle vehicle available in fleet");
        }

        List<VehicleEntity> socEligible = idleOnline.stream()
                .filter(vehicle -> normalizeSoc(vehicle.getBatteryLevel()) >= energy.getMinAssignableSoc())
                .filter(vehicle -> matchesRequiredVehicleType(order, vehicle))
                .toList();
        if (socEligible.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.LOW_SOC,
                    "All idle vehicles are below minimum assignable SOC");
        }

        List<VehicleEntity> reachableVehicles = new ArrayList<>();
        List<Double> parkDistances = new ArrayList<>();
        for (VehicleEntity vehicle : socEligible) {
            double distance = estimateRouteDistance(parkId, vehicle, pickup);
            if (Double.isInfinite(distance)) {
                continue;
            }
            reachableVehicles.add(vehicle);
            parkDistances.add(distance);
        }
        if (reachableVehicles.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "Pickup station is not reachable from any candidate vehicle on the road network");
        }

        List<Double> blendedDistances = dispatchGeoDistanceService.applyGeoBlend(reachableVehicles, pickup, parkDistances);
        List<ScoredCandidate> reachable = new ArrayList<>();
        for (int i = 0; i < reachableVehicles.size(); i++) {
            reachable.add(scoreCandidate(parkId, reachableVehicles.get(i), blendedDistances.get(i), energy, scoring));
        }

        reachable.sort(Comparator.comparingDouble(ScoredCandidate::totalScore));
        ScoredCandidate best = selectWithMapfReservation(parkId, pickup, reachable);
        if (best == null) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "No conflict-free MAPF route to pickup from any candidate vehicle");
        }
        String geoNote = dispatchGeoDistanceService.isGeoBlendEnabled() ? ", geoBlend=on" : "";
        String mapfNote = mapfRoutePlannerService.isEnabled() ? ", mapf=on" : "";
        String explanation = String.format(
                "Selected %s: distance=%.1f, socPenalty=%.1f, pluggedBonus=%.1f, total=%.1f%s%s",
                best.vehicle().getVehicleCode(),
                best.distanceScore(),
                best.socScore(),
                best.pluggedBonus(),
                best.totalScore(),
                geoNote,
                mapfNote);
        return DispatchAssignResult.success(best.vehicle(), explanation, best.totalScore(),
                best.distanceScore(), best.socScore(), best.pluggedBonus());
    }

    private ScoredCandidate selectWithMapfReservation(Long parkId, ParkStationResponse pickup,
                                                      List<ScoredCandidate> ranked) {
        if (!mapfRoutePlannerService.isEnabled()) {
            return ranked.get(0);
        }
        for (ScoredCandidate candidate : ranked) {
            VehicleEntity vehicle = candidate.vehicle();
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
        return pathLength(route);
    }

    private ScoredCandidate scoreCandidate(Long parkId, VehicleEntity vehicle, double distance,
                                           FleetEnergyProperties energy,
                                           DispatchScoringProperties scoring) {
        int soc = vehicle.getBatteryLevel() == null ? energy.getFullSoc() : vehicle.getBatteryLevel();
        double distanceScore = distance * scoring.getWeightDistance();
        distanceScore *= automationRuleService.resolvePeakDistanceFactor(parkId, peakModeService.isPeakMode(parkId) ? 0.85 : 1.0);
        double socScore = (energy.getFullSoc() - soc) * scoring.getWeightSocMargin();
        double pluggedBonus = 0D;
        Optional<FleetRuntime> runtime = fleetRuntimeService.get(vehicle.getId());
        if (runtime.isPresent()
                && Boolean.TRUE.equals(runtime.get().getPluggedIn())
                && "STANDBY".equals(runtime.get().getRuntimeStage())
                && soc >= energy.getFullSoc()) {
            pluggedBonus = scoring.getWeightPluggedStandbyBonus();
        }
        double total = distanceScore + socScore - pluggedBonus;
        return new ScoredCandidate(vehicle, distanceScore, socScore, pluggedBonus, total);
    }

    private int normalizeSoc(Integer batteryLevel) {
        return batteryLevel == null ? 100 : batteryLevel;
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

    private double pathLength(List<ParkPointResponse> route) {
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

    private record ScoredCandidate(VehicleEntity vehicle, double distanceScore, double socScore,
                                   double pluggedBonus, double totalScore) {
    }
}
