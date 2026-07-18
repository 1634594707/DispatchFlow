package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 服务位预约/锁定记录（P1-10）。
 * 防止多车同时占用同一服务位；支持预约超时自动释放。
 */
@Data
@TableName("t_station_service_position_reservation")
public class StationServicePositionReservationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long positionId;

    private Long stationId;

    private Long vehicleId;

    private Long taskId;

    /** 类型：LOCK（瞬时锁）/ RESERVATION（预约） */
    private String reservationType;

    /** 状态：ACTIVE/RELEASED/EXPIRED/CANCELLED */
    private String status;

    private LocalDateTime reservedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime releasedAt;

    /** 释放原因：COMPLETED/TIMEOUT/CANCELLED/REASSIGN */
    private String releaseReason;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
