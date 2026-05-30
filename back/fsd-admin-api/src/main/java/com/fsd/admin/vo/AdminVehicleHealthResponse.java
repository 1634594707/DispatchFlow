package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminVehicleHealthResponse {

    private Long vehicleId;

    private String vehicleCode;

    private int healthScore;

    private String healthLevel;

    private long openExceptionCount;

    private long failedTaskCount;

    private long maintenanceCount;

    private List<String> suggestions;
}
