package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 暂停派单全局开关表（V43 / P1-10）。
 * 统一控制自动派车、批量派车、紧急插队。
 */
@Data
@TableName("t_dispatch_pause_state")
public class DispatchPauseStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    /** 是否暂停派单（1=暂停，0=正常） */
    private Integer isPaused;

    private String pauseReason;

    private String pausedBy;

    private LocalDateTime pausedAt;

    private LocalDateTime resumedAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
