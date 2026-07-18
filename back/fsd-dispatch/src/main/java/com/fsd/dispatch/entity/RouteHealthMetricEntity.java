package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 路线健康指标表（V43 / P2-11）。
 * 持续监控路线系统的健康度。
 */
@Data
@TableName("t_route_health_metric")
public class RouteHealthMetricEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    /** 指标编码：EMPTY_ROAD_NETWORK/DISCONNECTED/NOT_SNAPPED/OFF_ROAD/CROSSES_BUILDING/STRAIGHT_LINE_FALLBACK/RESERVATION_CONFLICT */
    private String metricCode;

    private BigDecimal metricValue;

    /** 指标详情（结构化 JSON） */
    private String metricDetail;

    private LocalDateTime recordedAt;

    private LocalDateTime createdAt;
}
