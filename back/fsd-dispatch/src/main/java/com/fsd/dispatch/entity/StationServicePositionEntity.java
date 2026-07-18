package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 站点服务位子表（P0-5 / P1-7 / P1-10）。
 * 一个站点可配置多个服务位，避免「全部放在道路中心线上」造成排队外溢。
 */
@Data
@TableName("t_station_service_position")
public class StationServicePositionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private String positionCode;

    private String positionName;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private BigDecimal coordX;

    private BigDecimal coordY;

    /** 接入道路节点编码 */
    private String accessNodeCode;

    /** 服务方向：FORWARD/REVERSE/BIDIRECTIONAL */
    private String serviceDirection;

    /** V43: 到站车头朝向（度，0=北，顺时针；NULL=不限制） */
    private java.math.BigDecimal stopHeading;

    /** V43: 进入方向：FORWARD/REVERSE/LEFT/RIGHT */
    private String enterDirection;

    /** V43: 离开方向：FORWARD/REVERSE/LEFT/RIGHT */
    private String leaveDirection;

    /** 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    /** 服务位容量（同时停靠车辆数） */
    private Integer capacityLimit;

    /** 状态：ACTIVE/OCCUPIED/RESERVED/MAINTENANCE/OUT_OF_SERVICE */
    private String status;

    private Long reservedVehicleId;

    private LocalDateTime reservedUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
