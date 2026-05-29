package com.fsd.dispatch.dispatch;

import com.fsd.order.entity.OrderEntity;

public interface DispatchVehicleAssignService {

    DispatchAssignResult selectBestVehicle(OrderEntity order);
}
