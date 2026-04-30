package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminTaskQueryRequest {

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String status;

    private Boolean manualFlag;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
