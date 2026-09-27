package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.ParkingSlotStatus;
import com.fsd.common.enums.ParkingSlotType;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.dispatch.service.ParkingFacilityService;
import com.fsd.dispatch.vo.ParkPointResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkingFacilityServiceImpl implements ParkingFacilityService {

    private final ParkingSlotMapper parkingSlotMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingSessionService chargingSessionService;
    private final VehicleService vehicleService;

    public ParkingFacilityServiceImpl(ParkingSlotMapper parkingSlotMapper,
                                      ChargingPileMapper chargingPileMapper,
                                      ChargingSessionService chargingSessionService,
                                      VehicleService vehicleService) {
        this.parkingSlotMapper = parkingSlotMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.chargingSessionService = chargingSessionService;
        this.vehicleService = vehicleService;
    }

    @Override
    public Optional<ParkingSlotEntity> findSlotByVehicle(Long vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        Page<ParkingSlotEntity> page = parkingSlotMapper.selectPage(new Page<>(1, 1, false), new QueryWrapper<ParkingSlotEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("deleted", 0));
        List<ParkingSlotEntity> records = page.getRecords();
        return Optional.ofNullable(records.isEmpty() ? null : records.get(0));
    }

    @Override
    @Transactional
    public void releaseByVehicle(Long vehicleId) {
        if (vehicleId == null) {
            return;
        }
        int endSoc = resolveVehicleSoc(vehicleId);
        chargingSessionService.completeActiveSession(vehicleId, endSoc);
        parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
        chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
    }

    @Override
    @Transactional
    public void markCharging(Long parkId, Long vehicleId, String slotCode) {
        BindContext ctx = bindVehicleToSlot(parkId, vehicleId, slotCode, ParkingSlotStatus.CHARGING);
        chargingSessionService.startSession(parkId, vehicleId, ctx.slot().getId(), ctx.primaryPileId(), resolveVehicleSoc(vehicleId));
    }

    @Override
    @Transactional
    public void occupyPluggedStandby(Long parkId, Long vehicleId, String slotCode) {
        bindVehicleToSlot(parkId, vehicleId, slotCode, ParkingSlotStatus.OCCUPIED);
        chargingSessionService.completeActiveSession(vehicleId, resolveVehicleSoc(vehicleId));
    }

    @Override
    @Transactional
    public boolean reserveSlot(Long parkId, Long vehicleId, String slotCode) {
        if (parkId == null || vehicleId == null || slotCode == null || slotCode.isBlank()) {
            return false;
        }
        Page<ParkingSlotEntity> slotPage = parkingSlotMapper.selectPage(new Page<>(1, 1, false), new QueryWrapper<ParkingSlotEntity>()
                .eq("park_id", parkId)
                .eq("slot_code", slotCode)
                .eq("deleted", 0));
        List<ParkingSlotEntity> slotRecords = slotPage.getRecords();
        ParkingSlotEntity slot = slotRecords.isEmpty() ? null : slotRecords.get(0);
        if (slot == null) {
            return false;
        }
        if (vehicleId.equals(slot.getOccupiedVehicleId())) {
            // 这个位本来就记在这台车名下 ⇒ **续用**，不判成抢占失败，也不改行（状态由调用方维持）。
            // 少了这一条会自锁：车位行留着 OCCUPIED + 绑定而会话已经没了（重启丢内存态、#17 死锁回滚）时，
            // 真正站在这根桩上的那台车自己也抢不到它 —— 生产实测 22 根桩里有 6 根正是这个状态，
            // 而对账器**不该**放它们（车确实在位上），所以只能靠这里闭环。
            return true;
        }
        int slotUpdated = parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("id", slot.getId())
                .eq("status", ParkingSlotStatus.FREE.name())
                .eq("deleted", 0)
                .isNull("occupied_vehicle_id")
                .set("occupied_vehicle_id", vehicleId)
                .set("status", ParkingSlotStatus.RESERVED.name()));
        if (slotUpdated != 1) {
            // 抢位失败时**一行都不该动**。原来 `releaseReservation(vehicleId)` 写在方法开头，
            // 于是"试着去充电"这个动作本身就会把该车已经占着的待命位释放掉，
            // 而调用方只在 standbyPoint 为 null 时才重新取位 ⇒ 位丢了也不会再补，
            // 实测生产 20 台里只有 6 台占得到位。
            return false;
        }
        releaseOtherReservations(vehicleId, slot.getId());
        chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                .eq("parking_slot_id", slot.getId())
                .eq("status", ParkingSlotStatus.FREE.name())
                .eq("deleted", 0)
                .isNull("occupied_vehicle_id")
                .set("occupied_vehicle_id", vehicleId)
                .set("status", ParkingSlotStatus.RESERVED.name()));
        return true;
    }

    @Override
    @Transactional
    public void releaseSlotReservation(Long vehicleId, String slotCode) {
        if (vehicleId == null || slotCode == null || slotCode.isBlank()) {
            return;
        }
        parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("slot_code", slotCode)
                .eq("status", ParkingSlotStatus.RESERVED.name())
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
    }

    /** 像素坐标的"在场"容差（画布 1600×1854 对应约 4.2 km 宽，2 px ≈ 2.6 m）。 */
    private static final double PRESENT_TOLERANCE_PX = 2.0D;
    /** GCJ 坐标的"在场"容差 ≈ 2 m。SIM 行的 current_longitude 存像素、真车行存经纬度，所以两边都比一次。 */
    private static final double PRESENT_TOLERANCE_DEG = 0.00002D;

    @Override
    @Transactional
    public int releaseOrphanEnergySlots() {
        List<ParkingSlotEntity> orphans = parkingSlotMapper.selectList(new QueryWrapper<ParkingSlotEntity>()
                .eq("deleted", 0)
                .isNotNull("occupied_vehicle_id")
                // ⚠ 只放 OCCUPIED / CHARGING，**绝不能碰 RESERVED**：`reserveSlot` 之后、车开到桩之前
                // 那一段就是"RESERVED + 无会话 + 人不在位"，三条判据全都满足 —— 放掉它就是把正在
                // 路上的车的桩位抽走，它到桩前 markCharging 必抛"occupied by another vehicle"
                // （第 15 轮上线后 30 分钟内 26 条 ERROR 就是这么来的）。
                // 生产那 22 个孤儿全是 OCCUPIED，所以不收 RESERVED 不影响这次要修的问题。
                .in("status", ParkingSlotStatus.OCCUPIED.name(), ParkingSlotStatus.CHARGING.name())
                // 只管补能位：待命位没有会话可查，按①判会把正常待命的车全放掉
                .apply("EXISTS (SELECT 1 FROM t_charging_pile p"
                        + " WHERE p.parking_slot_id = t_parking_slot.id AND p.deleted = 0)")
                .apply("NOT EXISTS (SELECT 1 FROM t_charging_session cs"
                        + " WHERE cs.deleted = 0 AND cs.session_status = 'ACTIVE'"
                        + " AND cs.vehicle_id = t_parking_slot.occupied_vehicle_id)")
                .apply("NOT EXISTS (SELECT 1 FROM t_vehicle v"
                        + " WHERE v.id = t_parking_slot.occupied_vehicle_id AND v.deleted = 0"
                        + " AND ((ABS(t_parking_slot.coord_x - v.current_longitude) < {0}"
                        + " AND ABS(t_parking_slot.coord_y - v.current_latitude) < {1})"
                        + " OR (ABS(t_parking_slot.coord_lng - v.current_longitude) < {2}"
                        + " AND ABS(t_parking_slot.coord_lat - v.current_latitude) < {2})))",
                        PRESENT_TOLERANCE_PX, PRESENT_TOLERANCE_PX, PRESENT_TOLERANCE_DEG));
        for (ParkingSlotEntity slot : orphans) {
            parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                    .eq("id", slot.getId())
                    .set("occupied_vehicle_id", null)
                    .set("status", ParkingSlotStatus.FREE.name()));
            chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                    .eq("parking_slot_id", slot.getId())
                    .set("occupied_vehicle_id", null)
                    .set("status", ParkingSlotStatus.FREE.name()));
        }
        return orphans.size();
    }

    /** 释放这台车在**其它**位上的 RESERVED 绑定（保留刚占下的 `keepSlotId`）。 */
    private void releaseOtherReservations(Long vehicleId, Long keepSlotId) {
        parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .ne("id", keepSlotId)
                .eq("status", ParkingSlotStatus.RESERVED.name())
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
        chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .ne("parking_slot_id", keepSlotId)
                .eq("status", ParkingSlotStatus.RESERVED.name())
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
    }

    @Override
    @Transactional
    public void releaseReservation(Long vehicleId) {
        if (vehicleId == null) {
            return;
        }
        parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("status", ParkingSlotStatus.RESERVED.name())
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
        chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                .eq("occupied_vehicle_id", vehicleId)
                .eq("status", ParkingSlotStatus.RESERVED.name())
                .eq("deleted", 0)
                .set("occupied_vehicle_id", null)
                .set("status", ParkingSlotStatus.FREE.name()));
    }

    @Override
    @Transactional
    public Optional<ParkPointResponse> reserveChargingSlot(Long parkId, Long vehicleId, String preferredSlotCode) {
        List<String> candidates = listChargingSlotCodes(parkId, preferredSlotCode);
        for (String slotCode : candidates) {
            if (reserveSlot(parkId, vehicleId, slotCode)) {
                return Optional.of(toPoint(requireSlot(parkId, slotCode)));
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public Optional<ParkPointResponse> reserveStandbySlot(Long parkId, Long vehicleId) {
        if (parkId == null || vehicleId == null) {
            return Optional.empty();
        }
        // 幂等守卫：空闲态每个 tick 都会来问一次待命点，不续用同一个位就会让车在车位之间来回跳。
        Optional<ParkingSlotEntity> held = findSlotByVehicle(vehicleId);
        if (held.isPresent()) {
            // **任何**已绑定位都算"这辆车有位了"，不只是 STANDBY。原来只续用 STANDBY 型：
            // 插在桩上待命（plugged-in standby）的那批车手里是桩位，于是这里会给它们再发一个待命位，
            // 一台车挂两个位，而它原来那个 OCCUPIED 桩位没人再解（`releaseOtherReservations` 只放
            // RESERVED）⇒ 生产实测 16 台车双绑、22 个桩位成孤儿。
            return Optional.of(toPoint(held.get()));
        }
        List<ParkingSlotEntity> candidates = parkingSlotMapper.selectList(new QueryWrapper<ParkingSlotEntity>()
                .eq("park_id", parkId)
                .eq("slot_type", ParkingSlotType.STANDBY.name())
                .eq("deleted", 0)
                .orderByAsc("sort_order"));
        for (ParkingSlotEntity candidate : candidates) {
            if (reserveSlot(parkId, vehicleId, candidate.getSlotCode())) {
                return Optional.of(toPoint(requireSlot(parkId, candidate.getSlotCode())));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ParkPointResponse> listStandbySlots(Long parkId) {
        if (parkId == null) {
            return List.of();
        }
        return parkingSlotMapper.selectList(new QueryWrapper<ParkingSlotEntity>()
                        .eq("park_id", parkId)
                        .eq("slot_type", ParkingSlotType.STANDBY.name())
                        .eq("deleted", 0)
                        .isNotNull("coord_x")
                        .isNotNull("coord_y")
                        .orderByAsc("sort_order"))
                .stream()
                .map(this::toPoint)
                .toList();
    }

    @Override
    public List<ParkPointResponse> listSlotMarkers(Long parkId) {
        if (parkId == null) {
            return List.of();
        }
        return parkingSlotMapper.selectList(new QueryWrapper<ParkingSlotEntity>()
                        .eq("park_id", parkId)
                        .eq("deleted", 0)
                        .isNotNull("coord_lng")
                        .isNotNull("coord_lat")
                        .orderByAsc("sort_order"))
                .stream()
                .map(slot -> ParkPointResponse.builder()
                        .code(slot.getSlotCode())
                        .x(slot.getCoordX())
                        .y(slot.getCoordY())
                        .longitude(slot.getCoordLng())
                        .latitude(slot.getCoordLat())
                        .slotType(slot.getSlotType())
                        .status(slot.getStatus())
                        .occupiedVehicleId(slot.getOccupiedVehicleId())
                        .build())
                .toList();
    }

    private ParkPointResponse toPoint(ParkingSlotEntity slot) {
        return ParkPointResponse.builder()
                .code(slot.getSlotCode())
                .x(slot.getCoordX())
                .y(slot.getCoordY())
                .longitude(slot.getCoordLng())
                .latitude(slot.getCoordLat())
                .build();
    }

    private List<String> listChargingSlotCodes(Long parkId, String preferredSlotCode) {
        List<ChargingPileEntity> piles = chargingPileMapper.selectList(new QueryWrapper<ChargingPileEntity>()
                .eq("park_id", parkId)
                .eq("deleted", 0)
                .orderByAsc("sort_order"));
        Set<String> ordered = new LinkedHashSet<>();
        if (preferredSlotCode != null && !preferredSlotCode.isBlank()) {
            ordered.add(preferredSlotCode);
        }
        for (ChargingPileEntity pile : piles) {
            ParkingSlotEntity slot = parkingSlotMapper.selectById(pile.getParkingSlotId());
            if (slot != null && parkId.equals(slot.getParkId())) {
                ordered.add(slot.getSlotCode());
            }
        }
        return new ArrayList<>(ordered);
    }

    private BindContext bindVehicleToSlot(Long parkId, Long vehicleId, String slotCode, ParkingSlotStatus targetStatus) {
        if (parkId == null || vehicleId == null || slotCode == null || slotCode.isBlank()) {
            throw new BusinessException("PARKING_SLOT_INVALID", "Invalid parking bind request");
        }
        ParkingSlotEntity slot = requireSlot(parkId, slotCode);
        boolean reservedByVehicle = ParkingSlotStatus.RESERVED.name().equals(slot.getStatus())
                && vehicleId.equals(slot.getOccupiedVehicleId());
        if (slot.getOccupiedVehicleId() != null
                && !vehicleId.equals(slot.getOccupiedVehicleId())
                && !ParkingSlotStatus.FREE.name().equals(slot.getStatus())
                && !reservedByVehicle) {
            throw new BusinessException("PARKING_SLOT_CONFLICT",
                    "Slot " + slotCode + " is occupied by another vehicle");
        }
        if (!reservedByVehicle) {
            releaseByVehicle(vehicleId);
        }
        int slotUpdated = parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("id", slot.getId())
                .eq("deleted", 0)
                .and(wrapper -> wrapper.eq("status", ParkingSlotStatus.FREE.name())
                        .or(nested -> nested.eq("status", ParkingSlotStatus.RESERVED.name())
                                .eq("occupied_vehicle_id", vehicleId)))
                .set("occupied_vehicle_id", vehicleId)
                .set("status", targetStatus.name()));
        if (slotUpdated != 1) {
            throw new BusinessException("PARKING_SLOT_CONFLICT", "Failed to bind vehicle to slot " + slotCode);
        }
        List<ChargingPileEntity> piles = chargingPileMapper.selectList(new QueryWrapper<ChargingPileEntity>()
                .eq("parking_slot_id", slot.getId())
                .eq("deleted", 0));
        Long primaryPileId = null;
        for (ChargingPileEntity pile : piles) {
            primaryPileId = pile.getId();
            chargingPileMapper.update(null, new UpdateWrapper<ChargingPileEntity>()
                    .eq("id", pile.getId())
                    .and(wrapper -> wrapper.eq("status", ParkingSlotStatus.FREE.name())
                            .or(nested -> nested.eq("status", ParkingSlotStatus.RESERVED.name())
                                    .eq("occupied_vehicle_id", vehicleId)))
                    .set("occupied_vehicle_id", vehicleId)
                    .set("status", targetStatus.name()));
        }
        return new BindContext(slot, primaryPileId);
    }

    private int resolveVehicleSoc(Long vehicleId) {
        VehicleEntity vehicle = vehicleService.getById(vehicleId);
        return vehicle.getBatteryLevel() == null ? 0 : vehicle.getBatteryLevel();
    }

    private ParkingSlotEntity requireSlot(Long parkId, String slotCode) {
        Page<ParkingSlotEntity> slotPage = parkingSlotMapper.selectPage(new Page<>(1, 1, false), new QueryWrapper<ParkingSlotEntity>()
                .eq("park_id", parkId)
                .eq("slot_code", slotCode)
                .eq("deleted", 0));
        List<ParkingSlotEntity> slotRecords = slotPage.getRecords();
        ParkingSlotEntity slot = slotRecords.isEmpty() ? null : slotRecords.get(0);
        if (slot == null) {
            throw new BusinessException("PARKING_SLOT_NOT_FOUND", "Parking slot not found: " + slotCode);
        }
        return slot;
    }

    private record BindContext(ParkingSlotEntity slot, Long primaryPileId) {
    }
}
