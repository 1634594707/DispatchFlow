package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import java.util.List;
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

    private String orderPriority;

    private Long waitMinutes;

    private Integer openExceptionCount;

    private DispatchOpenExceptionBrief primaryOpenException;

    private List<DispatchOpenExceptionBrief> openExceptions;
}
