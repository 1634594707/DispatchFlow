package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkOrderSnapshotResponse {

    private Long orderId;

    private String orderNo;

    private String orderStatus;

    private Long taskId;

    private String taskNo;

    private String taskStatus;

    private Long vehicleId;

    private String vehicleCode;

    private String vehicleName;

    private String runtimeStage;

    private ParkStationResponse pickupStation;

    private ParkStationResponse dropoffStation;

    private LocalDateTime assignTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private LocalDateTime updatedAt;
}
