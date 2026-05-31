package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_battery_swap_session")
public class BatterySwapSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private Long cabinetId;

    private Long parkId;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
