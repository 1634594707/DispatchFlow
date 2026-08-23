package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkOrderCreateResponse {

    private Long orderId;

    private String orderNo;

    private String orderStatus;

    private Long taskId;

    private String taskNo;

    private String taskStatus;

    private Long vehicleId;

    private String message;

    /** true 表示本次响应来自幂等重放（重复提交返回原订单），而非新建订单。 */
    private Boolean replayed;
}
