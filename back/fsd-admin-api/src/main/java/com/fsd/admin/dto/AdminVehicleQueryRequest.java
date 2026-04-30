package com.fsd.admin.dto;

import lombok.Data;

@Data
public class AdminVehicleQueryRequest {

    private String vehicleCode;

    private String onlineStatus;

    private String dispatchStatus;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
