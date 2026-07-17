package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.ChargingSessionStatus;
import com.fsd.common.enums.ParkingSlotStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ChargingSessionEntity;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.math.BigDecimal;
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
    private final ParkRoutePlannerService parkRoutePlannerService;
    private final ParkGeoTransformService parkGeoTransformService;

    public ChargingSessionServiceImpl(ChargingSessionMapper chargingSessionMapper,
                                      ChargingPileMapper chargingPileMapper,
                                      ParkingSlotMapper parkingSlotMapper,
                                      VehicleMapper vehicleMapper,
                                      FleetEnergyProperties fleetEnergyProperties,
                                      ParkRoutePlannerService parkRoutePlannerService,
                                      ParkGeoTransformService parkGeoTransformService) {
        this.chargingSessionMapper = chargingSessionMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.parkingSlotMapper = parkingSlotMapper;
        this.vehicleMapper = vehicleMapper;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.parkRoutePlannerService = parkRoutePlannerService;
        this.parkGeoTransformService = parkGeoTransformService;
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
        Page<ChargingSessionEntity> page = chargingSessionMapper.selectPage(new Page<>(1, 1, false), new QueryWrapper<ChargingSessionEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("session_status", ChargingSessionStatus.ACTIVE.name())
                .eq("deleted", 0));
        List<ChargingSessionEntity> records = page.getRecords();
        return Optional.ofNullable(records.isEmpty() ? null : records.get(0));
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
        // Phase 4：车辆 currentLongitude/currentLatitude 实际存储 schematic x/y。
        // 解析真实 GPS 用于端点补全；schematic x/y 用于路网 buildRoute。
        BigDecimal vehicleX = vehicle.getCurrentLongitude();
        BigDecimal vehicleY = vehicle.getCurrentLatitude();
        Optional<GeoPoint> vehicleGeoOpt = parkGeoTransformService.toGcj02(vehicleX, vehicleY);

        List<ChargingPileEntity> freePiles = chargingPileMapper.selectList(new QueryWrapper<ChargingPileEntity>()
                .eq("status", ParkingSlotStatus.FREE.name())
                .eq("deleted", 0));
        if (freePiles.isEmpty()) {
            return null;
        }

        // Phase 5 任务 5.5：消除 N+1 查询，批量获取所有相关车位坐标。
        List<Long> slotIds = freePiles.stream()
                .map(ChargingPileEntity::getParkingSlotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Map<Long, ParkingSlotEntity> slotById = parkingSlotMapper
                .selectBatchIds(slotIds)
                .stream()
                .collect(Collectors.toMap(ParkingSlotEntity::getId, s -> s));

        // Phase 5 任务 5.5：负载均衡因子。统计每个 parkId 的活跃充电会话数，
        // 推荐时优先选择负载低的园区，使利用率方差 < 20%。
        java.util.Map<Long, Long> activeSessionsByPark = countActiveChargingSessionsByPark();

        Long bestPileId = null;
        double bestScore = Double.MAX_VALUE;
        for (ChargingPileEntity pile : freePiles) {
            ParkingSlotEntity slot = slotById.get(pile.getParkingSlotId());
            if (slot == null || slot.getCoordLng() == null || slot.getCoordLat() == null) {
                continue;
            }
            // Phase 4：改用路网 A* 距离替代直线 haversine
            double distance = roadNetworkDistanceMeters(slot.getParkId(),
                    vehicleX, vehicleY, slot.getCoordX(), slot.getCoordY(),
                    vehicleGeoOpt.orElse(null), new GeoPoint(slot.getCoordLng(), slot.getCoordLat()));
            // Phase 5 任务 5.5：综合评分 = 距离 * (1 + 负载因子)
            // 负载因子 = 该园区活跃充电会话数 / 10（归一化），负载越高评分越差
            long activeCount = activeSessionsByPark.getOrDefault(slot.getParkId(), 0L);
            double loadFactor = activeCount / 10.0D;
            double score = distance * (1.0D + loadFactor);
            if (score < bestScore) {
                bestScore = score;
                bestPileId = pile.getId();
            }
        }
        return bestPileId;
    }

    /** Phase 5 任务 5.5：按 parkId 统计当前活跃充电会话数，用于负载均衡。 */
    private java.util.Map<Long, Long> countActiveChargingSessionsByPark() {
        return chargingSessionMapper.selectList(new LambdaQueryWrapper<ChargingSessionEntity>()
                        .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.ACTIVE.name())
                        .eq(ChargingSessionEntity::getDeleted, 0))
                .stream()
                .filter(s -> s.getParkId() != null)
                .collect(Collectors.groupingBy(ChargingSessionEntity::getParkId, Collectors.counting()));
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

        // Phase 5 任务 5.4：纳入 BUSY 车辆的 SOC 预估。
        // BUSY 车辆当前 SOC 可能高于阈值，但完成任务后 SOC 将低于阈值；
        // 用预测 SOC（按 busyDrainMetersPerPercent + 平均速度推算）排序，
        // 排在 IDLE 低电量车辆之后（IDLE 立即可充，BUSY 需等任务结束）。
        List<VehicleEntity> busyPredictedLow = predictBusyVehiclesBelowThreshold(threshold);

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
            // 高峰期 BUSY 预测低电量车辆排到最后
            busyPredictedLow.stream()
                    .sorted(bySocAsc)
                    .forEach(highSoc::add);
            sorted = highSoc;
        } else {
            List<VehicleEntity> merged = lowSocVehicles.stream()
                    .sorted(bySocAsc)
                    .collect(Collectors.toCollection(ArrayList::new));
            busyPredictedLow.stream()
                    .sorted(bySocAsc)
                    .forEach(merged::add);
            sorted = merged;
        }

        return sorted.stream().map(VehicleEntity::getId).toList();
    }

    /**
     * Phase 5 任务 5.4：预测 BUSY 车辆在完成当前任务后 SOC 是否会低于阈值。
     * 估算窗口 = 30 分钟（与 predictChargingDemand 的 lookahead 对齐）。
     */
    private List<VehicleEntity> predictBusyVehiclesBelowThreshold(int threshold) {
        int lookaheadMinutes = 30;
        double metersPerPercent = Math.max(50D, fleetEnergyProperties.getBusyDrainMetersPerPercent());
        double drainPerMinute = (AVG_SPEED_MPS * 60D) / metersPerPercent;
        double estimatedDrain = drainPerMinute * lookaheadMinutes;

        return vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .eq(VehicleEntity::getDispatchStatus, VehicleDispatchStatus.BUSY.name()))
                .stream()
                .filter(v -> v.getBatteryLevel() != null && v.getBatteryLevel() >= threshold)
                .filter(v -> (v.getBatteryLevel() - estimatedDrain) < threshold)
                .toList();
    }

    @Override
    public double estimateDistanceToNearestChargingPile(Long parkId, java.math.BigDecimal fromLng,
                                                          java.math.BigDecimal fromLat) {
        if (fromLng == null || fromLat == null) {
            return Double.MAX_VALUE;
        }
        GeoPoint fromGeo = new GeoPoint(fromLng, fromLat);
        // Phase 4：将 GPS 转换为 schematic x/y 以便路网 buildRoute 使用
        Optional<ParkGeoTransformService.ParkPoint> fromXY = parkGeoTransformService.fromGcj02(fromLng, fromLat);
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
            BigDecimal fromX = fromXY.map(ParkGeoTransformService.ParkPoint::x).orElse(null);
            BigDecimal fromY = fromXY.map(ParkGeoTransformService.ParkPoint::y).orElse(null);
            // Phase 4：改用路网 A* 距离（米），回退到直线 haversine
            double distance = roadNetworkDistanceMeters(parkId,
                    fromX, fromY, slot.getCoordX(), slot.getCoordY(), fromGeo, pileGeo);
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

    /**
     * Phase 4：使用路网 A* 距离（米）替代直线 haversine。
     * 当路网不可用（空图/不可达/缺 schematic 坐标）时回退到直线 haversine。
     */
    private double roadNetworkDistanceMeters(Long parkId,
                                              BigDecimal fromX, BigDecimal fromY,
                                              BigDecimal toX, BigDecimal toY,
                                              GeoPoint fromGeo, GeoPoint toGeo) {
        if (fromX == null || fromY == null || toX == null || toY == null) {
            return haversineOrMax(fromGeo, toGeo);
        }
        try {
            List<ParkPointResponse> route = parkRoutePlannerService.buildRoute(parkId, fromX, fromY, toX, toY);
            if (route == null || route.size() < 2) {
                return haversineOrMax(fromGeo, toGeo);
            }
            // 端点补全 GPS，使 pathLength 全程使用 haversine（米）
            ParkPointResponse start = route.get(0);
            if (start.getLongitude() == null && fromGeo != null) {
                start.setLongitude(fromGeo.longitude());
                start.setLatitude(fromGeo.latitude());
            }
            ParkPointResponse end = route.get(route.size() - 1);
            if (end.getLongitude() == null && toGeo != null) {
                end.setLongitude(toGeo.longitude());
                end.setLatitude(toGeo.latitude());
            }
            return pathLengthMeters(route);
        } catch (BusinessException ex) {
            // PARK_ROAD_NETWORK_EMPTY / PARK_ROUTE_NOT_FOUND → 回退直线距离
            return haversineOrMax(fromGeo, toGeo);
        }
    }

    private static double haversineOrMax(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return Double.MAX_VALUE;
        }
        return GeoPolygonUtils.haversineMeters(from, to);
    }

    private static double pathLengthMeters(List<ParkPointResponse> route) {
        if (route == null || route.size() < 2) {
            return 0D;
        }
        double total = 0D;
        ParkPointResponse prev = route.get(0);
        for (int i = 1; i < route.size(); i++) {
            ParkPointResponse curr = route.get(i);
            if (prev.getLongitude() != null && prev.getLatitude() != null
                    && curr.getLongitude() != null && curr.getLatitude() != null) {
                total += GeoPolygonUtils.haversineMeters(
                        new GeoPoint(prev.getLongitude(), prev.getLatitude()),
                        new GeoPoint(curr.getLongitude(), curr.getLatitude()));
            } else if (prev.getX() != null && prev.getY() != null && curr.getX() != null && curr.getY() != null) {
                total += Math.hypot(curr.getX().doubleValue() - prev.getX().doubleValue(),
                        curr.getY().doubleValue() - prev.getY().doubleValue());
            }
            prev = curr;
        }
        return total;
    }
}
