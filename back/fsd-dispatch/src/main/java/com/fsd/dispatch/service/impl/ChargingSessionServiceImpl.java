package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fsd.common.enums.ChargingSessionStatus;
import com.fsd.dispatch.entity.ChargingSessionEntity;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.service.ChargingSessionService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargingSessionServiceImpl implements ChargingSessionService {

    private final ChargingSessionMapper chargingSessionMapper;

    public ChargingSessionServiceImpl(ChargingSessionMapper chargingSessionMapper) {
        this.chargingSessionMapper = chargingSessionMapper;
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
}
