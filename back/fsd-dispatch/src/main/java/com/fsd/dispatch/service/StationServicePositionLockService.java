package com.fsd.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fsd.dispatch.entity.StationServicePositionEntity;
import com.fsd.dispatch.entity.StationServicePositionReservationEntity;
import com.fsd.dispatch.mapper.StationServicePositionMapper;
import com.fsd.dispatch.mapper.StationServicePositionReservationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站点服务位预约/锁定服务（P1-10）。
 *
 * 防止多个车辆同时占用同一个服务位：
 *   - lockServicePosition(positionId, vehicleId, taskId)：瞬时锁，强制独占。
 *   - reserveServicePosition(positionId, vehicleId, taskId, expiresAt)：预约，超时自动释放。
 *   - releaseServicePosition(positionId, vehicleId, reason)：释放或取消预约。
 *   - sweepExpiredReservations()：清理过期预约（由调度器周期调用）。
 *
 * 不变性约束：
 *   - 同一服务位最多一个 ACTIVE 预约/锁定。
 *   - 同一车辆最多持有一个 ACTIVE 服务位预约。
 *   - 释放时必须写明原因（COMPLETED/TIMEOUT/CANCELLED/REASSIGN）。
 */
@Service
public class StationServicePositionLockService {

    private static final Logger log = LoggerFactory.getLogger(StationServicePositionLockService.class);

    private final StationServicePositionMapper positionMapper;
    private final StationServicePositionReservationMapper reservationMapper;

    public StationServicePositionLockService(StationServicePositionMapper positionMapper,
                                              StationServicePositionReservationMapper reservationMapper) {
        this.positionMapper = positionMapper;
        this.reservationMapper = reservationMapper;
    }

    /**
     * 瞬时锁定服务位（不设过期时间，必须显式释放）。
     * @return true=锁定成功；false=已被占用或预约
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean lockServicePosition(Long positionId, Long vehicleId, Long taskId) {
        return acquire(positionId, vehicleId, taskId, "LOCK", null);
    }

    /**
     * 预约服务位（带过期时间，超时自动释放）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reserveServicePosition(Long positionId, Long vehicleId, Long taskId, LocalDateTime expiresAt) {
        return acquire(positionId, vehicleId, taskId, "RESERVATION", expiresAt);
    }

    /**
     * 释放服务位（COMPLETED/TIMEOUT/CANCELLED/REASSIGN）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseServicePosition(Long positionId, Long vehicleId, String reason) {
        if (positionId == null || vehicleId == null) {
            return false;
        }
        StationServicePositionReservationEntity existing = findActive(positionId);
        if (existing == null || !vehicleId.equals(existing.getVehicleId())) {
            return false;
        }
        existing.setStatus("RELEASED");
        existing.setReleasedAt(LocalDateTime.now());
        existing.setReleaseReason(reason == null ? "COMPLETED" : reason);
        reservationMapper.updateById(existing);

        StationServicePositionEntity position = positionMapper.selectById(positionId);
        if (position != null) {
            position.setStatus("ACTIVE");
            position.setReservedVehicleId(null);
            position.setReservedUntil(null);
            positionMapper.updateById(position);
        }
        log.info("Service position {} released by vehicle {} reason={}", positionId, vehicleId, reason);
        return true;
    }

    /**
     * 清理过期预约（由调度器周期调用）。
     * @return 释放的预约数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int sweepExpiredReservations() {
        List<StationServicePositionReservationEntity> expired = reservationMapper.selectList(
                new QueryWrapper<StationServicePositionReservationEntity>()
                        .eq("status", "ACTIVE")
                        .isNotNull("expires_at")
                        .lt("expires_at", LocalDateTime.now()));
        int count = 0;
        for (StationServicePositionReservationEntity res : expired) {
            res.setStatus("EXPIRED");
            res.setReleasedAt(LocalDateTime.now());
            res.setReleaseReason("TIMEOUT");
            reservationMapper.updateById(res);

            StationServicePositionEntity position = positionMapper.selectById(res.getPositionId());
            if (position != null) {
                position.setStatus("ACTIVE");
                position.setReservedVehicleId(null);
                position.setReservedUntil(null);
                positionMapper.updateById(position);
            }
            count++;
        }
        if (count > 0) {
            log.info("Swept {} expired service position reservations", count);
        }
        return count;
    }

    /** 查询某站点当前可用的服务位（status=ACTIVE 且无预约）。 */
    public List<StationServicePositionEntity> findAvailablePositions(Long stationId) {
        return positionMapper.selectList(new QueryWrapper<StationServicePositionEntity>()
                .eq("station_id", stationId)
                .eq("status", "ACTIVE")
                .isNull("reserved_vehicle_id")
                .eq("deleted", 0));
    }

    /** 查询某车辆当前持有的服务位预约。 */
    public StationServicePositionReservationEntity findActiveReservationByVehicle(Long vehicleId) {
        return reservationMapper.selectOne(new QueryWrapper<StationServicePositionReservationEntity>()
                .eq("vehicle_id", vehicleId)
                .eq("status", "ACTIVE")
                .eq("deleted", 0)
                .last("LIMIT 1"));
    }

    private boolean acquire(Long positionId, Long vehicleId, Long taskId,
                             String type, LocalDateTime expiresAt) {
        if (positionId == null || vehicleId == null) {
            return false;
        }
        StationServicePositionEntity position = positionMapper.selectById(positionId);
        if (position == null || position.getDeleted() != null && position.getDeleted() != 0) {
            return false;
        }
        if ("OUT_OF_SERVICE".equalsIgnoreCase(position.getStatus())
                || "MAINTENANCE".equalsIgnoreCase(position.getStatus())) {
            return false;
        }
        StationServicePositionReservationEntity existing = findActive(positionId);
        if (existing != null && !vehicleId.equals(existing.getVehicleId())) {
            return false;
        }

        if (existing != null) {
            // 同一车辆重新预约 — 更新过期时间
            existing.setReservationType(type);
            existing.setExpiresAt(expiresAt);
            existing.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(existing);
        } else {
            StationServicePositionReservationEntity reservation = new StationServicePositionReservationEntity();
            reservation.setPositionId(positionId);
            reservation.setStationId(position.getStationId());
            reservation.setVehicleId(vehicleId);
            reservation.setTaskId(taskId);
            reservation.setReservationType(type);
            reservation.setStatus("ACTIVE");
            reservation.setReservedAt(LocalDateTime.now());
            reservation.setExpiresAt(expiresAt);
            reservationMapper.insert(reservation);
        }

        position.setStatus("RESERVED");
        position.setReservedVehicleId(vehicleId);
        position.setReservedUntil(expiresAt);
        positionMapper.updateById(position);
        log.info("Service position {} {} by vehicle {} taskId={} expiresAt={}",
                positionId, type, vehicleId, taskId, expiresAt);
        return true;
    }

    private StationServicePositionReservationEntity findActive(Long positionId) {
        return reservationMapper.selectOne(new QueryWrapper<StationServicePositionReservationEntity>()
                .eq("position_id", positionId)
                .eq("status", "ACTIVE")
                .eq("deleted", 0)
                .last("LIMIT 1"));
    }
}
