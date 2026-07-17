package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_park_geofence")
public class ParkGeofenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String fenceCode;

    private String fenceName;

    /** BOUNDARY = alert on exit; RESTRICTED = alert on enter */
    private String fenceType;

    /**
     * 响应级别（阶段六 6.1）：
     * INFO = 仅记录日志，不写异常、不触发自动化规则；
     * WARN = 记录异常并告警，但不触发紧急停车/升级流程；
     * BLOCK = 记录异常并触发紧急停车（最高响应级别）。
     */
    private String responseLevel;

    /**
     * GPS 缓冲距离（米，阶段六 6.2）。
     * 用于 GEOFENCE_EXIT 时的边界容差判定，替代原硬编码 GPS_BUFFER_METERS=15.0。
     */
    private BigDecimal bufferMeters;

    private String polygonJson;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
