package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.vo.VehicleCommandResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.entity.VehicleEntity;

public interface VehicleCommandService {

    void issueDispatchCommandIfNeeded(VehicleEntity vehicle, DispatchTaskEntity task, OrderEntity order);

    VehicleCommandResponse pollNextCommand(String vehicleCode);

    void acknowledgeCommand(String vehicleCode, Long commandId);

    void failCommand(String vehicleCode, Long commandId, String reason);
}
