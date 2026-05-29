package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminTaskQueryRequest {

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String status;

    private Boolean manualFlag;

    /** 仅返回存在 OPEN 异常的任务 */
    private Boolean withOpenExceptionOnly;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
