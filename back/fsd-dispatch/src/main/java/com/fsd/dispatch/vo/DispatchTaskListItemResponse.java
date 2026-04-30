package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchTaskListItemResponse {

    private Long taskId;

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String status;

    private String failReasonCode;

    private String failReasonMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
