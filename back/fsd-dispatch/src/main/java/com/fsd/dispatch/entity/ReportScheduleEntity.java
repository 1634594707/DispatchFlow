package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_report_schedule")
public class ReportScheduleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String cronExpression;

    private String recipients;

    private Integer enabled;

    private LocalDateTime lastSentAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
