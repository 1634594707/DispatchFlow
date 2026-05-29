package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchFleetMetricsResponse {

    private int assignableVehicleCount;

    private int pluggedStandbyCount;

    private int chargingCount;

    private int onlineVehicleCount;
}
