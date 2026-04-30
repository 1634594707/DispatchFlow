package com.fsd.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class VehicleReportRequest {

    @NotBlank(message = "vehicleCode is required")
    private String vehicleCode;

    @NotBlank(message = "onlineStatus is required")
    private String onlineStatus;

    @NotBlank(message = "dispatchStatus is required")
    private String dispatchStatus;

    private Long taskId;

    private Long orderId;

    @NotBlank(message = "reportType is required")
    private String reportType;

    @NotNull(message = "reportTime is required")
    private LocalDateTime reportTime;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Integer batteryLevel;

    private String resultCode;

    private String resultMessage;
}
