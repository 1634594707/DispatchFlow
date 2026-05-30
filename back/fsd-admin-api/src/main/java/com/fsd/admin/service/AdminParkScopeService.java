package com.fsd.admin.service;

import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;

public interface AdminParkScopeService {

    boolean matchesOrder(Long orderId, Long parkId);

    boolean matchesVehicle(VehicleAdminListItemResponse vehicle, Long parkId);

    boolean matchesVehicleSnapshot(ParkVehicleSnapshotResponse vehicle, Long parkId);

    Long resolveDefaultParkId();

    OrderEntity findOrder(Long orderId);
}
