package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchTaskDetailResponse {

    private Long taskId;

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String dispatchType;

    private String status;

    private String failReasonCode;

    private String failReasonMsg;

    private LocalDateTime assignTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Integer manualFlag;

    private Integer retryCount;

    private String remark;
}
