package com.fsd.order.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderDetailResponse {

    private Long orderId;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
