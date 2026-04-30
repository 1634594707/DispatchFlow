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
}
