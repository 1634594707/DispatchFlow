package com.fsd.admin.service;

import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderDetailResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import org.springframework.stereotype.Service;

@Service
public class OrderAdminDetailService {

    private final OrderQueryService orderQueryService;
    private final ParkStationService parkStationService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final ParkPilotService parkPilotService;

    public OrderAdminDetailService(OrderQueryService orderQueryService,
                                   ParkStationService parkStationService,
                                   DispatchTaskMapper dispatchTaskMapper,
                                   VehicleMapper vehicleMapper,
                                   ParkPilotService parkPilotService) {
        this.orderQueryService = orderQueryService;
        this.parkStationService = parkStationService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.parkPilotService = parkPilotService;
    }

    public OrderDetailResponse getEnrichedDetail(Long orderId) {
        OrderDetailResponse detail = orderQueryService.getOrderDetail(orderId);
        enrichStations(detail);
        enrichTaskAndVehicle(detail);
        enrichRuntimeStage(detail);
        return detail;
    }

    private void enrichStations(OrderDetailResponse detail) {
        if (detail.getPickupPointId() != null) {
            try {
                ParkStationResponse pickup = parkStationService.requireStation(detail.getPickupPointId());
                detail.setPickupPointName(pickup.getStationName());
                detail.setPickupStationCode(pickup.getStationCode());
            } catch (RuntimeException ignored) {
                // station may have been removed
            }
        }
        if (detail.getDropoffPointId() != null) {
            try {
                ParkStationResponse dropoff = parkStationService.requireStation(detail.getDropoffPointId());
                detail.setDropoffPointName(dropoff.getStationName());
                detail.setDropoffStationCode(dropoff.getStationCode());
            } catch (RuntimeException ignored) {
                // station may have been removed
            }
        }
    }

    private void enrichTaskAndVehicle(OrderDetailResponse detail) {
        if (detail.getDispatchTaskId() == null) {
            return;
        }
        DispatchTaskEntity task = dispatchTaskMapper.selectById(detail.getDispatchTaskId());
        if (task == null || (task.getDeleted() != null && task.getDeleted() != 0)) {
            return;
        }
        if (task.getVehicleId() == null) {
            return;
        }
        detail.setVehicleId(task.getVehicleId());
        VehicleEntity vehicle = vehicleMapper.selectById(task.getVehicleId());
        if (vehicle != null) {
            detail.setVehicleCode(vehicle.getVehicleCode());
        }
    }

    private void enrichRuntimeStage(OrderDetailResponse detail) {
        parkPilotService.listOrderSnapshots().stream()
                .filter(snapshot -> detail.getOrderId().equals(snapshot.getOrderId()))
                .findFirst()
                .map(ParkOrderSnapshotResponse::getRuntimeStage)
                .ifPresent(detail::setRuntimeStage);
    }
}
