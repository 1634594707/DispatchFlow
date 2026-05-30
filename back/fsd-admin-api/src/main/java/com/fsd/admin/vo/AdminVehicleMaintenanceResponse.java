package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminVehicleMaintenanceResponse {

    private Long id;

    private Long vehicleId;

    private String vehicleCode;

    private String maintenanceType;

    private String description;

    private LocalDateTime maintenanceAt;

    private String operatorName;

    private String status;

    private String remark;

    private LocalDateTime createdAt;
}
