package com.fsd.vehicle.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleReportResponse {

    private String vehicleCode;

    private String taskStatus;

    private String orderStatus;

    private String vehicleDispatchStatus;

    private String message;
}
