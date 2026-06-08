package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_report_history")
public class ReportHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportType;

    private String reportName;

    private String dataset;

    private String period;

    private LocalDate date;

    private Long parkId;

    private Long fileSizeBytes;

    private String generatedBy;

    private LocalDateTime generatedAt;
}