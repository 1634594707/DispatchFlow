package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fsd.common.enums.ChargingSessionStatus;
import com.fsd.common.enums.ParkingSlotStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ChargingSessionEntity;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargingSessionServiceImpl implements ChargingSessionService {

    /** Average speed (m/s) used to estimate future SOC drain for busy vehicles. */
    private static final double AVG_SPEED_MPS = 1.5D;

    /** SOC threshold above which vehicles are prioritized during peak hours. */
    private static final int PEAK_HIGH_SOC_THRESHOLD = 50;

    /** Peak-hour windows for charging queue prioritization. */
    private static final LocalTime PEAK_MORNING_START = LocalTime.of(8, 0);
    private static final LocalTime PEAK_MORNING_END = LocalTime.of(10, 0);
    private static final LocalTime PEAK_EVENING_START = LocalTime.of(17, 0);
    private static final LocalTime PEAK_EVENING_END = LocalTime.of(19, 0);

    private final ChargingSessionMapper chargingSessionMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final ParkingSlotMapper parkingSlotMapper;
    private final VehicleMapper vehicleMapper;
    private final FleetEnergyProperties fleetEnergyProperties;

    public ChargingSessionServiceImpl(ChargingSessionMapper chargingSessionMapper,
                                      ChargingPileMapper chargingPileMapper,
                                      ParkingSlotMapper parkingSlotMapper,
                                      VehicleMapper vehicleMapper,
                                      FleetEnergyProperties fleetEnergyProperties) {
        this.chargingSessionMapper = chargingSessionMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.parkingSlotMapper = parkingSlotMapper;
        this.vehicleMapper = vehicleMapper;
        this.fleetEnergyProperties = fleetEnergyProperties;
    }

    @Override
    @Transactional
    public ChargingSessionEntity startSession(Long parkId, Long vehicleId, Long parkingSlotId,
                                              Long chargingPileId, int startSoc) {
        completeActiveSession(vehicleId, startSoc);
        ChargingSessionEntity entity = new ChargingSessionEntity();
        entity.setParkId(parkId);
        entity.setVehicleId(vehicleId);
        entity.setParkingSlotId(parkingSlotId);
        entity.setChargingPileId(chargingPileId);
        entity.setSessionStatus(ChargingSessionStatus.ACTIVE.name());
        entity.setStartSoc(startSoc);
        entity.setStartTime(LocalDateTime.now());
        entity.setVersion(0);
        entity.setDeleted(0);
        chargingSessionMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public void completeActiveSession(Long vehicleId, int endSoc) {
        if (vehicleId == null) {
            return;
        }
        chargingSessionMapper.update(null, new UpdateWrapper<ChargingSessionEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("session_status", ChargingSessionStatus.ACTIVE.name())
                .eq("deleted", 0)
                .set("session_status", ChargingSessionStatus.COMPLETED.name())
                .set("end_soc", endSoc)
                .set("end_time", LocalDateTime.now()));
    }

    @Override
    public Optional<ChargingSessionEntity> findActiveByVehicle(Long vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(chargingSessionMapper.selectOne(new QueryWrapper<ChargingSessionEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("session_status", ChargingSessionStatus.ACTIVE.name())
                .eq("deleted", 0)
                .last("limit 1")));
    }

    @Override
    public int predictChargingDemand(Long parkId, int lookaheadMinutes) {
        int threshold = fleetEnergyProperties.getReturnToChargeThreshold();

        List<VehicleEntity> onlineVehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getOnlineStatus, VehicleOnlineStatus.ONLINE.name()));

        int lowSocCount = 0;
        for (VehicleEntity vehicle : onlineVehicles) {
            Integer soc = vehicle.getBatteryLevel();
            if (soc != null && soc < threshold) {
                lowSocCount++;
            }
        }

        double metersPerPercent = Math.max(50D, fleetEnergyProperties.getBusyDrainMetersPerPercent());
        double drainPerMinute = (AVG_SPEED_MPS * 60D) / metersPerPercent;
        double estimatedDrain = drainPerMinute * Math.max(0, lookaheadMinutes);

        List<VehicleEntity> busyVehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.BUSY.name()));

        int estimatedLowCount = 0;
        for (VehicleEntity vehicle : busyVehicles) {
            Integer soc = vehicle.getBatteryLevel();
            if (soc == null || soc < threshold) {
                continue;
            }
            double futureSoc = soc - estimatedDrain;
            if (futureSoc < threshold) {
                estimatedLowCount++;
            }
        }

        return lowSocCount + estimatedLowCount;
    }

    @Override
    public Long recommendChargingPile(Long vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null
                || vehicle.getCurrentLongitude() == null
                || vehicle.getCurrentLatitude() == null) {
            return null;
        }
        GeoPoint vehicleGeo = new GeoPoint(vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude());

        List<ChargingPileEntity> freePiles = chargingPileMapper.selectList(new QueryWrapper<ChargingPileEntity>()
                .eq("status", ParkingSlotStatus.FREE.name())
                .eq("deleted", 0));

        Long nearestPileId = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ChargingPileEntity pile : freePiles) {
            ParkingSlotEntity slot = parkingSlotMapper.selectById(pile.getParkingSlotId());
            if (slot == null || slot.getCoordLng() == null || slot.getCoordLat() == null) {
                continue;
            }
            GeoPoint pileGeo = new GeoPoint(slot.getCoordLng(), slot.getCoordLat());
            double distance = GeoPolygonUtils.haversineMeters(vehicleGeo, pileGeo);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPileId = pile.getId();
            }
        }
        return nearestPileId;
    }

    @Override
    public List<Long> getChargingQueue(Long parkId) {
        int threshold = fleetEnergyProperties.getReturnToChargeThreshold();

        List<VehicleEntity> lowSocVehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.IDLE.name()))
                .stream()
                .filter(v -> v.getBatteryLevel() != null && v.getBatteryLevel() < threshold)
                .toList();

        Comparator<VehicleEntity> bySocAsc = Comparator.comparingInt(VehicleEntity::getBatteryLevel);

        List<VehicleEntity> sorted;
        if (isPeakHour()) {
            List<VehicleEntity> highSoc = lowSocVehicles.stream()
                    .filter(v -> v.getBatteryLevel() != null && v.getBatteryLevel() > PEAK_HIGH_SOC_THRESHOLD)
                    .sorted(bySocAsc)
                    .collect(Collectors.toCollection(ArrayList::new));
            lowSocVehicles.stream()
                    .filter(v -> v.getBatteryLevel() == null || v.getBatteryLevel() <= PEAK_HIGH_SOC_THRESHOLD)
                    .sorted(bySocAsc)
                    .forEach(highSoc::add);
            sorted = highSoc;
        } else {
            sorted = lowSocVehicles.stream()
                    .sorted(bySocAsc)
                    .toList();
        }

        return sorted.stream().map(VehicleEntity::getId).toList();
    }

    @Override
    public double estimateDistanceToNearestChargingPile(Long parkId, java.math.BigDecimal fromLng,
                                                          java.math.BigDecimal fromLat) {
        if (fromLng == null || fromLat == null) {
            return Double.MAX_VALUE;
        }
        GeoPoint fromGeo = new GeoPoint(fromLng, fromLat);
        // Consider all charging piles regardless of status — when evaluating whether a
        // vehicle can complete a task, the pile might be free by the time it returns.
        List<ChargingPileEntity> piles = chargingPileMapper.selectList(new QueryWrapper<ChargingPileEntity>()
                .eq("deleted", 0));
        double nearest = Double.MAX_VALUE;
        for (ChargingPileEntity pile : piles) {
            ParkingSlotEntity slot = parkingSlotMapper.selectById(pile.getParkingSlotId());
            if (slot == null || slot.getCoordLng() == null || slot.getCoordLat() == null) {
                continue;
            }
            GeoPoint pileGeo = new GeoPoint(slot.getCoordLng(), slot.getCoordLat());
            double distance = GeoPolygonUtils.haversineMeters(fromGeo, pileGeo);
            if (distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    @Override
    @Transactional
    public int timeoutStaleChargingSessions() {
        int timeoutMinutes = fleetEnergyProperties.getChargingTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<ChargingSessionEntity> stale = chargingSessionMapper.selectList(new LambdaQueryWrapper<ChargingSessionEntity>()
                .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.ACTIVE.name())
                .eq(ChargingSessionEntity::getDeleted, 0)
                .lt(ChargingSessionEntity::getStartTime, cutoff));
        if (stale.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChargingSessionEntity session : stale) {
            session.setSessionStatus(ChargingSessionStatus.TIMED_OUT.name());
            session.setEndTime(LocalDateTime.now());
            session.setRemark("ALG-10: timed out after " + timeoutMinutes + " minutes");
            chargingSessionMapper.updateById(session);
            // Note: the vehicle's dispatch_status is NOT modified here. Charging vehicles
            // stay in IDLE status (charging is tracked only via ChargingSessionEntity), so
            // there is no status transition needed — freeing the pile/slot below is enough
            // to make the vehicle available for re-dispatch. Avoiding the dispatch_status
            // update also prevents clobbering a manual operator action that may have
            // transitioned the vehicle to BUSY/UNAVAILABLE in the meantime.
            // Free the parking slot/pile if it was held.
            if (session.getChargingPileId() != null) {
                chargingPileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ChargingPileEntity>()
                        .eq("id", session.getChargingPileId())
                        .set("status", ParkingSlotStatus.FREE.name()));
            }
            if (session.getParkingSlotId() != null) {
                parkingSlotMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ParkingSlotEntity>()
                        .eq("id", session.getParkingSlotId())
                        .set("status", ParkingSlotStatus.FREE.name()));
            }
            count++;
        }
        return count;
    }

    private static boolean isPeakHour() {
        LocalTime now = LocalTime.now();
        boolean morningPeak = !now.isBefore(PEAK_MORNING_START) && now.isBefore(PEAK_MORNING_END);
        boolean eveningPeak = !now.isBefore(PEAK_EVENING_START) && now.isBefore(PEAK_EVENING_END);
        return morningPeak || eveningPeak;
    }
}
