package com.fsd.dispatch.dispatch;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.service.ParkStationService;
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
    private final FleetChargePolicy fleetChargePolicy;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final DispatchScoringProperties dispatchScoringProperties;
    private final FleetRuntimeService fleetRuntimeService;

    public DispatchVehicleAssignServiceImpl(VehicleService vehicleService,
                                            ParkStationService parkStationService,
                                            ParkRoutePlannerService parkRoutePlannerService,
                                            FleetChargePolicy fleetChargePolicy,
                                            FleetEnergyProperties fleetEnergyProperties,
                                            DispatchScoringProperties dispatchScoringProperties,
                                            FleetRuntimeService fleetRuntimeService) {
        this.vehicleService = vehicleService;
        this.parkStationService = parkStationService;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.fleetChargePolicy = fleetChargePolicy;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.dispatchScoringProperties = dispatchScoringProperties;
        this.fleetRuntimeService = fleetRuntimeService;
    }

    @Override
    public DispatchAssignResult selectBestVehicle(OrderEntity order) {
        Long parkId = resolveParkId(order);
        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
        parkStationService.assertStationInPark(order.getPickupPointId(), parkId);

        List<VehicleEntity> idleOnline = vehicleService.listAssignableVehicles();
        if (idleOnline.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.NO_VEHICLE,
                    "No online idle vehicle available in fleet");
        }

        List<VehicleEntity> socEligible = idleOnline.stream()
                .filter(fleetChargePolicy::isAssignable)
                .toList();
        if (socEligible.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.LOW_SOC,
                    "All idle vehicles are below minimum assignable SOC");
        }

        List<ScoredCandidate> reachable = new ArrayList<>();
        for (VehicleEntity vehicle : socEligible) {
            double distance = estimateRouteDistance(parkId, vehicle, pickup);
            if (Double.isInfinite(distance)) {
                continue;
            }
            reachable.add(scoreCandidate(vehicle, distance));
        }
        if (reachable.isEmpty()) {
            return DispatchAssignResult.failure(DispatchAssignFailReason.UNREACHABLE,
                    "Pickup station is not reachable from any candidate vehicle on the road network");
        }

        ScoredCandidate best = reachable.stream()
                .min(Comparator.comparingDouble(ScoredCandidate::totalScore))
                .orElseThrow();
        String explanation = String.format(
                "Selected %s: distance=%.1f, socPenalty=%.1f, pluggedBonus=%.1f, total=%.1f",
                best.vehicle().getVehicleCode(),
                best.distanceScore(),
                best.socScore(),
                best.pluggedBonus(),
                best.totalScore());
        return DispatchAssignResult.success(best.vehicle(), explanation, best.totalScore(),
                best.distanceScore(), best.socScore(), best.pluggedBonus());
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

    private ScoredCandidate scoreCandidate(VehicleEntity vehicle, double distance) {
        int soc = vehicle.getBatteryLevel() == null ? fleetEnergyProperties.getFullSoc() : vehicle.getBatteryLevel();
        double distanceScore = distance * dispatchScoringProperties.getWeightDistance();
        double socScore = (fleetEnergyProperties.getFullSoc() - soc) * dispatchScoringProperties.getWeightSocMargin();
        double pluggedBonus = 0D;
        Optional<FleetRuntime> runtime = fleetRuntimeService.get(vehicle.getId());
        if (runtime.isPresent()
                && Boolean.TRUE.equals(runtime.get().getPluggedIn())
                && "STANDBY".equals(runtime.get().getRuntimeStage())
                && fleetChargePolicy.isFullyCharged(soc)) {
            pluggedBonus = dispatchScoringProperties.getWeightPluggedStandbyBonus();
        }
        double total = distanceScore + socScore - pluggedBonus;
        return new ScoredCandidate(vehicle, distanceScore, socScore, pluggedBonus, total);
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
