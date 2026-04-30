package com.fsd.order.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderAdminListItemResponse {

    private Long orderId;

    private String orderNo;

    private String externalOrderNo;

    private String status;

    private String priority;

    private Long dispatchTaskId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
