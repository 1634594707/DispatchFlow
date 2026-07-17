package com.fsd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_order")
public class OrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String externalOrderNo;

    private String sourceType;

    private String bizType;

    private Long parkId;

    private Long routeId;

    private Long pickupPointId;

    private Long dropoffPointId;

    private String priority;

    private String status;

    private Long dispatchTaskId;

    private String remark;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    /** 配送区域 */
    private String deliveryZone;

    /** 货物重量(kg) */
    private BigDecimal weight;

    private Integer deleted;
}
