package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.DispatchRouteEntity;
import java.util.List;
import java.util.Optional;

public interface DispatchRouteService {

    List<DispatchRouteEntity> listRoutes(Long parkId);

    Optional<DispatchRouteEntity> findRoute(Long routeId);

    Optional<DispatchRouteEntity> matchRouteByStations(Long parkId, Long pickupStationId, Long dropoffStationId);

    int countActiveTasksOnRoute(Long routeId);

    boolean isRouteWithinServiceWindow(DispatchRouteEntity route);

    boolean isRouteOccupancyAvailable(DispatchRouteEntity route);
}
