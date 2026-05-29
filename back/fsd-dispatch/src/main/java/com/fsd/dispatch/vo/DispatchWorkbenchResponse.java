package com.fsd.dispatch.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchWorkbenchResponse {

    private DispatchInterventionQueueResponse intervention;

    private DispatchFleetMetricsResponse fleetMetrics;

    private ParkLayoutResponse parkLayout;

    private List<ParkVehicleSnapshotResponse> vehicles;
}
