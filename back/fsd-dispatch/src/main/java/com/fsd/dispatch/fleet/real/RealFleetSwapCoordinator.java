package com.fsd.dispatch.fleet.real;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.entity.BatterySwapCabinetEntity;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tracks battery swap sessions for REAL fleet vehicles based on telemetry stages
 * ({@code TO_SWAP} / {@code SWAPPING}) and cabinet target codes.
 */
@Component
public class RealFleetSwapCoordinator {

    private final BatterySwapSessionService swapSessionService;
    private final BatterySwapCabinetMapper swapCabinetMapper;
    private final FleetChargePolicy fleetChargePolicy;
    private final DispatchStrategyRuntimeService strategyRuntimeService;

    @Value("${fsd.automation.default-park-id:1}")
    private long defaultParkId;

    public RealFleetSwapCoordinator(BatterySwapSessionService swapSessionService,
                                    BatterySwapCabinetMapper swapCabinetMapper,
                                    FleetChargePolicy fleetChargePolicy,
                                    DispatchStrategyRuntimeService strategyRuntimeService) {
        this.swapSessionService = swapSessionService;
        this.swapCabinetMapper = swapCabinetMapper;
        this.fleetChargePolicy = fleetChargePolicy;
        this.strategyRuntimeService = strategyRuntimeService;
    }

    public void onTelemetry(VehicleEntity vehicle, VehicleTelemetryRequest request, FleetRuntime previous) {
        if (vehicle == null || request == null) {
            return;
        }
        String stage = request.getRuntimeStage();
        String previousStage = previous != null ? previous.getRuntimeStage() : null;

        if (fleetChargePolicy.isActivelySwapping(stage)) {
            if (swapSessionService.findActiveByVehicle(vehicle.getId()).isEmpty()) {
                resolveCabinet(request.getTargetCode(), defaultParkId).ifPresent(cabinet ->
                        swapSessionService.startSession(
                                defaultParkId,
                                vehicle.getId(),
                                cabinet.getId(),
                                request.getSoc() == null ? 0 : request.getSoc()));
            }
            return;
        }

        if (previousStage != null && fleetChargePolicy.isActivelySwapping(previousStage)) {
            swapSessionService.completeActiveSession(vehicle.getId());
        }
    }

    public boolean prefersSwapRecovery(VehicleEntity vehicle, long parkId) {
        FleetEnergyProperties energy = strategyRuntimeService.energyForAssign(parkId);
        String mode = energy.getEnergyRecoveryMode() == null ? "CHARGE" : energy.getEnergyRecoveryMode();
        if ("SWAP".equalsIgnoreCase(mode)) {
            return findSwapCabinet(parkId).isPresent();
        }
        if ("AUTO".equalsIgnoreCase(mode) && vehicle != null && vehicle.getId() != null) {
            return vehicle.getId() % 2 == 0 && findSwapCabinet(parkId).isPresent();
        }
        return false;
    }

    private Optional<BatterySwapCabinetEntity> resolveCabinet(String targetCode, long parkId) {
        if (targetCode != null && !targetCode.isBlank()) {
            Page<BatterySwapCabinetEntity> byCodePage = swapCabinetMapper.selectPage(new Page<>(1, 1, false), new LambdaQueryWrapper<BatterySwapCabinetEntity>()
                    .eq(BatterySwapCabinetEntity::getDeleted, 0)
                    .eq(BatterySwapCabinetEntity::getStatus, "ACTIVE")
                    .eq(BatterySwapCabinetEntity::getParkId, parkId)
                    .eq(BatterySwapCabinetEntity::getCabinetCode, targetCode));
            List<BatterySwapCabinetEntity> byCodeRecords = byCodePage.getRecords();
            BatterySwapCabinetEntity byCode = byCodeRecords.isEmpty() ? null : byCodeRecords.get(0);
            if (byCode != null) {
                return Optional.of(byCode);
            }
        }
        return findSwapCabinet(parkId);
    }

    private Optional<BatterySwapCabinetEntity> findSwapCabinet(long parkId) {
        Page<BatterySwapCabinetEntity> page = swapCabinetMapper.selectPage(new Page<>(1, 1, false), new LambdaQueryWrapper<BatterySwapCabinetEntity>()
                .eq(BatterySwapCabinetEntity::getDeleted, 0)
                .eq(BatterySwapCabinetEntity::getStatus, "ACTIVE")
                .eq(BatterySwapCabinetEntity::getParkId, parkId));
        List<BatterySwapCabinetEntity> records = page.getRecords();
        return Optional.ofNullable(records.isEmpty() ? null : records.get(0));
    }
}
