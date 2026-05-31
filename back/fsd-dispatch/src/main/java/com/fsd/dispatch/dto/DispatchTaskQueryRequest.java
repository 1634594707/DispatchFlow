package com.fsd.dispatch.dto;

import lombok.Data;

@Data
public class DispatchTaskQueryRequest {

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String status;

    private Boolean manualFlag;

    private Boolean withOpenExceptionOnly;

    private Long parkId;

    private String poolStatus;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
