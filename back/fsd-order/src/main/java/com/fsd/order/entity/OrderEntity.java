package com.fsd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    private Integer deleted;
}
