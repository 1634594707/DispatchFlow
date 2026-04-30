package com.fsd.dispatch.service.impl;

import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;

public interface ParkPilotSimulationService {

    void initializeVehiclesIfNeeded();

    List<ParkVehicleSnapshotResponse> buildSnapshots(List<VehicleEntity> vehicles);
}
