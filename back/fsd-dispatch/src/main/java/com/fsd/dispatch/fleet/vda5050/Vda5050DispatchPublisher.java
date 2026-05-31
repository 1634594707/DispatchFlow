package com.fsd.dispatch.fleet.vda5050;

import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.VehicleCommandEntity;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;

public interface Vda5050DispatchPublisher {

    void publishDispatchOrder(VehicleEntity vehicle,
                              VehicleCommandEntity command,
                              DispatchTaskEntity task,
                              ParkStationResponse pickup,
                              ParkStationResponse dropoff);
}
