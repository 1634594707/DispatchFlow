package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchExceptionListItemResponse {

    private Long id;

    private Long taskId;

    private String taskNo;

    private String taskStatus;

    private String taskFailReasonCode;

    private String taskFailReasonMsg;

    private Long orderId;

    private Long vehicleId;

    private String exceptionType;

    private String exceptionStatus;

    private String exceptionMsg;

    private String severity;

    private String resolveAction;

    private LocalDateTime occurTime;

    private LocalDateTime resolvedTime;

    private String resolverId;

    private String resolveRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
