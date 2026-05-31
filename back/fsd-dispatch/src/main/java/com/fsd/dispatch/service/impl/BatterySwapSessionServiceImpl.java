package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fsd.dispatch.entity.BatterySwapSessionEntity;
import com.fsd.dispatch.mapper.BatterySwapSessionMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatterySwapSessionServiceImpl implements BatterySwapSessionService {

    private final BatterySwapSessionMapper swapSessionMapper;

    public BatterySwapSessionServiceImpl(BatterySwapSessionMapper swapSessionMapper) {
        this.swapSessionMapper = swapSessionMapper;
    }

    @Override
    @Transactional
    public BatterySwapSessionEntity startSession(Long parkId, Long vehicleId, Long cabinetId, int startSoc) {
        completeActiveSession(vehicleId);
        BatterySwapSessionEntity entity = new BatterySwapSessionEntity();
        entity.setParkId(parkId);
        entity.setVehicleId(vehicleId);
        entity.setCabinetId(cabinetId);
        entity.setStatus("IN_PROGRESS");
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        swapSessionMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public void completeActiveSession(Long vehicleId) {
        if (vehicleId == null) {
            return;
        }
        swapSessionMapper.update(null, new UpdateWrapper<BatterySwapSessionEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("status", "IN_PROGRESS")
                .set("status", "COMPLETED")
                .set("finished_at", LocalDateTime.now()));
    }

    @Override
    public Optional<BatterySwapSessionEntity> findActiveByVehicle(Long vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(swapSessionMapper.selectOne(new QueryWrapper<BatterySwapSessionEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("status", "IN_PROGRESS")
                .last("limit 1")));
    }
}
