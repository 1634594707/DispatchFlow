package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBatchTaskItemResult {

    private Long taskId;

    private String taskNo;

    private boolean success;

    private String message;

    private String status;

    private Long vehicleId;
}
