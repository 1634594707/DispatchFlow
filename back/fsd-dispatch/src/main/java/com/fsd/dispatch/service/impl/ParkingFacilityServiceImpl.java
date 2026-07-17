package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.ParkingSlotStatus;
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
        releaseReservation(vehicleId);
        Page<ParkingSlotEntity> slotPage = parkingSlotMapper.selectPage(new Page<>(1, 1, false), new QueryWrapper<ParkingSlotEntity>()
                .eq("park_id", parkId)
                .eq("slot_code", slotCode)
                .eq("deleted", 0));
        List<ParkingSlotEntity> slotRecords = slotPage.getRecords();
        ParkingSlotEntity slot = slotRecords.isEmpty() ? null : slotRecords.get(0);
        if (slot == null) {
            return false;
        }
        int slotUpdated = parkingSlotMapper.update(null, new UpdateWrapper<ParkingSlotEntity>()
                .eq("id", slot.getId())
                .eq("status", ParkingSlotStatus.FREE.name())
                .eq("deleted", 0)
                .isNull("occupied_vehicle_id")
                .set("occupied_vehicle_id", vehicleId)
                .set("status", ParkingSlotStatus.RESERVED.name()));
        if (slotUpdated != 1) {
            return false;
        }
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
                ParkingSlotEntity slot = requireSlot(parkId, slotCode);
                return Optional.of(ParkPointResponse.builder()
                        .code(slot.getSlotCode())
                        .x(slot.getCoordX())
                        .y(slot.getCoordY())
                        .build());
            }
        }
        return Optional.empty();
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
