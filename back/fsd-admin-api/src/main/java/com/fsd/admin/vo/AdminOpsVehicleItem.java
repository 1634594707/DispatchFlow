package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOpsVehicleItem {

    private Long vehicleId;

    private String vehicleCode;

    private Integer soc;

    private long offlineMinutes;
}
