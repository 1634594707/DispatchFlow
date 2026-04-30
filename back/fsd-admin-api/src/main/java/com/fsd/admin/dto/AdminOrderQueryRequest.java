package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminOrderQueryRequest {

    private String orderNo;

    private String externalOrderNo;

    private String status;

    private String priority;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
