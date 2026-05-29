package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_charging_session")
public class ChargingSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private Long vehicleId;

    private Long chargingPileId;

    private Long parkingSlotId;

    private String sessionStatus;

    private Integer startSoc;

    private Integer endSoc;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
