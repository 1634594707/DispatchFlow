package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.StationType;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.service.HubCapacityService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class HubCapacityServiceImpl implements HubCapacityService {

    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "PENDING", "MANUAL_PENDING", "ASSIGNED", "EXECUTING");

    private final StationMapper stationMapper;
    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final ParkStationService parkStationService;

    public HubCapacityServiceImpl(StationMapper stationMapper,
                                  OrderMapper orderMapper,
                                  DispatchTaskMapper dispatchTaskMapper,
                                  ParkStationService parkStationService) {
        this.stationMapper = stationMapper;
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.parkStationService = parkStationService;
    }

    @Override
    public boolean isHubCapacityAvailable(Long stationId) {
        StationEntity station = stationMapper.selectById(stationId);
        if (station == null || station.getDeleted() != null && station.getDeleted() == 1) {
            return true;
        }
        if (station.getCapacityLimit() == null || station.getCapacityLimit() <= 0) {
            return true;
        }
        return countOccupancy(stationId) < station.getCapacityLimit();
    }

    @Override
    public int countOccupancy(Long stationId) {
        List<OrderEntity> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getDeleted, 0)
                .and(wrapper -> wrapper
                        .eq(OrderEntity::getPickupPointId, stationId)
                        .or()
                        .eq(OrderEntity::getDropoffPointId, stationId)));
        if (orders.isEmpty()) {
            return 0;
        }
        List<Long> orderIds = orders.stream().map(OrderEntity::getId).toList();
        Long count = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .in(DispatchTaskEntity::getOrderId, orderIds)
                .in(DispatchTaskEntity::getStatus, ACTIVE_TASK_STATUSES));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public boolean isHubLikeStation(ParkStationResponse station) {
        if (station == null || station.getStationType() == null) {
            return false;
        }
        String type = station.getStationType();
        return StationType.HUB.name().equals(type)
                || StationType.BUFFER.name().equals(type)
                || StationType.MOTHERSHIP.name().equals(type);
    }

    public ParkStationResponse requireStation(Long stationId) {
        return parkStationService.requireStation(stationId);
    }

    public boolean stationsShareHubConstraint(Long pickupId, Long dropoffId) {
        ParkStationResponse pickup = parkStationService.requireStation(pickupId);
        ParkStationResponse dropoff = parkStationService.requireStation(dropoffId);
        if (isHubLikeStation(pickup) && !isHubCapacityAvailable(pickupId)) {
            return false;
        }
        if (isHubLikeStation(dropoff) && !isHubCapacityAvailable(dropoffId)) {
            return false;
        }
        return !Objects.equals(pickupId, dropoffId)
                || isHubCapacityAvailable(pickupId);
    }
}
