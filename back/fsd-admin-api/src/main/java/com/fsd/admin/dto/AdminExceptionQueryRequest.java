package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminExceptionQueryRequest {

    private String exceptionType;

    private String exceptionStatus;

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
