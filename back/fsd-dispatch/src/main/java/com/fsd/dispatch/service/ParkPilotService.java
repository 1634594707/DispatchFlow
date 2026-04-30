package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.List;

public interface ParkPilotService {

    ParkLayoutResponse getLayout();

    List<ParkStationResponse> listStations();

    ParkStationResponse getStation(Long stationId);

    VehicleEntity selectNearestVehicle(List<VehicleEntity> candidates, Long stationId);

    List<ParkVehicleSnapshotResponse> listVehicleSnapshots();

    List<ParkOrderSnapshotResponse> listOrderSnapshots();
}
