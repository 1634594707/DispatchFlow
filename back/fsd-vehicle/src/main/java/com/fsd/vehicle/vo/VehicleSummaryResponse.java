package com.fsd.vehicle.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleSummaryResponse {

    private long onlineCount;

    private long idleCount;

    private long busyCount;
}
