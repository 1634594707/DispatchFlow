package com.fsd.order.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderDetailResponse {

    private Long orderId;

    private String orderNo;

    private String externalOrderNo;

    private String sourceType;

    private String bizType;

    private Long pickupPointId;

    private Long dropoffPointId;

    /** 取货站名称（管理端富化） */
    private String pickupPointName;

    /** 取货站代码（管理端富化） */
    private String pickupStationCode;

    /** 送货站名称（管理端富化） */
    private String dropoffPointName;

    /** 送货站代码（管理端富化） */
    private String dropoffStationCode;

    private Long vehicleId;

    private String vehicleCode;

    /** 车辆运行阶段（短驳地理快照，管理端富化） */
    private String runtimeStage;

    private String priority;

    private String status;

    private Long dispatchTaskId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
