package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchRouteEntity;
import com.fsd.dispatch.entity.DispatchRouteStationEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchRouteMapper;
import com.fsd.dispatch.mapper.DispatchRouteStationMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DispatchRouteServiceImpl implements DispatchRouteService {

    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "PENDING", "MANUAL_PENDING", "ASSIGNED", "EXECUTING");

    private final DispatchRouteMapper dispatchRouteMapper;
    private final DispatchRouteStationMapper dispatchRouteStationMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final OrderMapper orderMapper;

    public DispatchRouteServiceImpl(DispatchRouteMapper dispatchRouteMapper,
                                    DispatchRouteStationMapper dispatchRouteStationMapper,
                                    DispatchTaskMapper dispatchTaskMapper,
                                    OrderMapper orderMapper) {
        this.dispatchRouteMapper = dispatchRouteMapper;
        this.dispatchRouteStationMapper = dispatchRouteStationMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public List<DispatchRouteEntity> listRoutes(Long parkId) {
        LambdaQueryWrapper<DispatchRouteEntity> wrapper = new LambdaQueryWrapper<DispatchRouteEntity>()
                .eq(DispatchRouteEntity::getDeleted, 0)
                .orderByAsc(DispatchRouteEntity::getRouteCode);
        if (parkId != null) {
            wrapper.eq(DispatchRouteEntity::getParkId, parkId);
        }
        return dispatchRouteMapper.selectList(wrapper);
    }

    @Override
    public Optional<DispatchRouteEntity> findRoute(Long routeId) {
        if (routeId == null) {
            return Optional.empty();
        }
        DispatchRouteEntity route = dispatchRouteMapper.selectOne(new LambdaQueryWrapper<DispatchRouteEntity>()
                .eq(DispatchRouteEntity::getId, routeId)
                .eq(DispatchRouteEntity::getDeleted, 0));
        return Optional.ofNullable(route);
    }

    @Override
    public Optional<DispatchRouteEntity> matchRouteByStations(Long parkId, Long pickupStationId, Long dropoffStationId) {
        List<DispatchRouteEntity> routes = listRoutes(parkId).stream()
                .filter(route -> "ACTIVE".equals(route.getStatus()))
                .toList();
        for (DispatchRouteEntity route : routes) {
            List<DispatchRouteStationEntity> stops = dispatchRouteStationMapper.selectList(
                    new LambdaQueryWrapper<DispatchRouteStationEntity>()
                            .eq(DispatchRouteStationEntity::getRouteId, route.getId())
                            .orderByAsc(DispatchRouteStationEntity::getSequenceNo));
            if (stops.size() < 2) {
                continue;
            }
            Long first = stops.get(0).getStationId();
            Long last = stops.get(stops.size() - 1).getStationId();
            if (Objects.equals(first, pickupStationId) && Objects.equals(last, dropoffStationId)) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }

    @Override
    public int countActiveTasksOnRoute(Long routeId) {
        List<OrderEntity> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getDeleted, 0)
                .eq(OrderEntity::getRouteId, routeId));
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
    public boolean isRouteWithinServiceWindow(DispatchRouteEntity route) {
        if (route == null) {
            return true;
        }
        LocalTime start = route.getServiceStartTime();
        LocalTime end = route.getServiceEndTime();
        if (start == null || end == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        if (start.isBefore(end) || start.equals(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    @Override
    public boolean isRouteOccupancyAvailable(DispatchRouteEntity route) {
        if (route == null || route.getMaxConcurrentTasks() == null || route.getMaxConcurrentTasks() <= 0) {
            return true;
        }
        return countActiveTasksOnRoute(route.getId()) < route.getMaxConcurrentTasks();
    }

    public List<DispatchRouteStationEntity> listRouteStations(Long routeId) {
        return dispatchRouteStationMapper.selectList(new LambdaQueryWrapper<DispatchRouteStationEntity>()
                .eq(DispatchRouteStationEntity::getRouteId, routeId)
                .orderByAsc(DispatchRouteStationEntity::getSequenceNo));
    }

    public DispatchRouteEntity requireRoute(Long routeId) {
        return findRoute(routeId).orElseThrow(() ->
                new com.fsd.common.exception.BusinessException("ROUTE_NOT_FOUND", "线路不存在"));
    }
}
