package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_station")
public class StationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String stationCode;

    private String stationName;

    private String stationType;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String area;

    private String status;

    private Integer sortOrder;

    private Integer capacityLimit;

    /** 营业时间窗口，例如 "06:00-22:00" */
    private String serviceHours;

    /** 平均服务时长（秒），用于 ETA 与队列估算 */
    private Integer avgServiceSeconds;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    /** 配送区域: GEO_DELIVERY / SCHEMATIC / GENERAL */
    private String deliveryZone;

    private Integer deleted;
}
