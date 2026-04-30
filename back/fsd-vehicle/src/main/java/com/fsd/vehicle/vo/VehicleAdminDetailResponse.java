package com.fsd.vehicle.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleAdminDetailResponse {

    private Long vehicleId;

    private String vehicleCode;

    private String vehicleName;

    private String vehicleType;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private BigDecimal currentLatitude;

    private BigDecimal currentLongitude;

    private Integer batteryLevel;

    private LocalDateTime lastReportTime;

    private String remark;
}
