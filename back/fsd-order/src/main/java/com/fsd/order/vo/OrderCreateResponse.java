package com.fsd.order.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderCreateResponse {

    private Long orderId;

    private String orderNo;

    private String status;
}
