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

    /** 取货点 GCJ-02 原始坐标（用户下单点的那个位置）。与 pickupPointId 二选一。 */
    private BigDecimal pickupLng;

    private BigDecimal pickupLat;

    private BigDecimal dropoffLng;

    private BigDecimal dropoffLat;

    /** 受理时吸附到的可派单路网节点（t_road_node.node_code）。派单只认它，不认原始坐标。 */
    private String pickupNodeCode;

    private String dropoffNodeCode;

    /** 吸附距离（米）：唯一能事后回答"用户点在不在没路的地方"的证据。 */
    private BigDecimal pickupSnapMeters;

    private BigDecimal dropoffSnapMeters;

    private String priority;

    private String status;

    private Long dispatchTaskId;

    private String remark;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 货物重量(kg) */
    private BigDecimal weight;

    private Integer deleted;
}
