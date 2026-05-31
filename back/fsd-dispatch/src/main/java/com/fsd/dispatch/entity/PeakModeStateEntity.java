package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_peak_mode_state")
public class PeakModeStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String mode;

    private String templateCode;

    private String scheduleCron;

    private String scheduleEndCron;

    private LocalDateTime lastSchedulePeakAt;

    private LocalDateTime lastScheduleEndAt;

    private LocalDateTime enabledAt;

    private LocalDateTime updatedAt;
}
