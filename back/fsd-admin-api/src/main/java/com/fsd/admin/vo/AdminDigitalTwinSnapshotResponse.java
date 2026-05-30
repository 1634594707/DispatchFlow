package com.fsd.admin.vo;

import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDigitalTwinSnapshotResponse {

    private ParkLayoutResponse layout;

    private List<ParkVehicleSnapshotResponse> vehicles;

    private int pendingTaskCount;

    private int openExceptionCount;

    private int idleVehicleCount;

    private int lowBatteryVehicleCount;
}
