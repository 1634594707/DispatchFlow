package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminExceptionQueryRequest {

    private String exceptionType;

    private String exceptionStatus;

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    /** 按关联任务状态过滤，如 MANUAL_PENDING */
    private String taskStatus;

    /** 仅返回关联任务为 MANUAL_PENDING 的异常 */
    private Boolean onlyManualPendingTask;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
