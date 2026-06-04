package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchTaskDetailResponse {

    private Long taskId;

    private String taskNo;

    private Long orderId;

    /** 取货站代码（管理端富化） */
    private String pickupStationCode;

    /** 取货站名称（管理端富化） */
    private String pickupPointName;

    /** 送货站代码（管理端富化） */
    private String dropoffStationCode;

    /** 送货站名称（管理端富化） */
    private String dropoffPointName;

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

    private Integer openExceptionCount;

    private List<DispatchOpenExceptionBrief> openExceptions;
}
