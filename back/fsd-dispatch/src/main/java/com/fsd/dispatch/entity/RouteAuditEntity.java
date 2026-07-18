package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 路线审计表（V43 / P0-3.1 / P2-11）。
 * 每次路线规划保存审计信息，支持规划路线与实际轨迹对比。
 */
@Data
@TableName("t_route_audit")
public class RouteAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路线ID（UUID 或业务编码） */
    private String routeId;

    private Long taskId;

    private Long vehicleId;

    private Long parkId;

    private Long mapVersionId;

    private String mapVersionCode;

    /** 路线模式：REAL_ROAD/SCHEMATIC/STRAIGHT_LINE */
    private String routeMode;

    /** 路线来源：AMAP/LOCAL_GRAPH/STRAIGHT_LINE */
    private String source;

    private BigDecimal originLng;

    private BigDecimal originLat;

    private BigDecimal destinationLng;

    private BigDecimal destinationLat;

    /** 规划路线 polyline（GCJ-02 坐标点 JSON 数组） */
    private String plannedPolyline;

    /** 实际轨迹 polyline（运行后回填） */
    private String actualPolyline;

    private BigDecimal plannedLengthMeters;

    private BigDecimal actualLengthMeters;

    /** 最大偏航距离（米） */
    private BigDecimal deviationMeters;

    private Integer rerouteCount;

    /** 是否经过碰撞校验（1=是） */
    private Integer collisionChecked;

    private Integer crossesBuilding;

    private Integer crossesRiver;

    /** 不可达原因编码（RouteUnreachableReason） */
    private String unreachableReason;

    /** 状态：PLANNED/EXECUTING/COMPLETED/FAILED/DEVIATED */
    private String status;

    private LocalDateTime plannedAt;

    private LocalDateTime executedAt;

    private LocalDateTime completedAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
