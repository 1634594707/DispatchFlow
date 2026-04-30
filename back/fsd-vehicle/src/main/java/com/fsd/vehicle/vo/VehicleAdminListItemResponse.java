package com.fsd.vehicle.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleAdminListItemResponse {

    private Long vehicleId;

    private String vehicleCode;

    private String vehicleName;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private Integer batteryLevel;

    private LocalDateTime lastReportTime;
}
