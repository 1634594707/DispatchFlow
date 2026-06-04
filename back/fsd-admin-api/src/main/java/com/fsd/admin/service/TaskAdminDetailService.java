package com.fsd.admin.service;

import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;

@Service
public class TaskAdminDetailService {

    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final OrderMapper orderMapper;
    private final ParkStationService parkStationService;

    public TaskAdminDetailService(DispatchAdminQueryService dispatchAdminQueryService,
                                  OrderMapper orderMapper,
                                  ParkStationService parkStationService) {
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.orderMapper = orderMapper;
        this.parkStationService = parkStationService;
    }

    public DispatchTaskDetailResponse getEnrichedDetail(Long taskId) {
        DispatchTaskDetailResponse detail = dispatchAdminQueryService.getTaskDetail(taskId);
        if (detail.getOrderId() == null) {
            return detail;
        }
        OrderEntity order = orderMapper.selectById(detail.getOrderId());
        if (order == null) {
            return detail;
        }
        enrichStation(detail, order.getPickupPointId(), true);
        enrichStation(detail, order.getDropoffPointId(), false);
        return detail;
    }

    private void enrichStation(DispatchTaskDetailResponse detail, Long stationId, boolean pickup) {
        if (stationId == null) {
            return;
        }
        try {
            ParkStationResponse station = parkStationService.requireStation(stationId);
            if (pickup) {
                detail.setPickupPointName(station.getStationName());
                detail.setPickupStationCode(station.getStationCode());
            } else {
                detail.setDropoffPointName(station.getStationName());
                detail.setDropoffStationCode(station.getStationCode());
            }
        } catch (RuntimeException ignored) {
            // station may have been removed
        }
    }
}
