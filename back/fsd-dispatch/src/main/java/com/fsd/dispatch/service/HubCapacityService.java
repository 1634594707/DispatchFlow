package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkStationResponse;

public interface HubCapacityService {

    boolean isHubCapacityAvailable(Long stationId);

    int countOccupancy(Long stationId);

    boolean isHubLikeStation(ParkStationResponse station);
}
