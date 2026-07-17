package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import java.util.List;

public interface ParkStationService {

    ParkEntity requireDefaultPark();

    ParkEntity requirePark(Long parkId);

    ParkEntity requireParkByCode(String parkCode);

    List<ParkResponse> listActiveParks();

    List<ParkStationResponse> listStations(Long parkId);

    ParkStationResponse requireStation(Long stationId);

    void assertStationsBelongToSamePark(Long pickupStationId, Long dropoffStationId);

    void assertStationInPark(Long stationId, Long parkId);

    /** 站点 GCJ-02 须落在园区 ACTIVE BOUNDARY 围栏内（找家纺 L1 试点）。 */
    void assertStationWithinDeliveryZone(Long stationId, Long parkId);

    /**
     * 检查站点是否在公共道路旁（可配送）
     * @param stationId 站点ID
     * @return true如果站点可达
     */
    boolean isStationRoadAccessible(Long stationId);

    ParkStationResponse toStationResponse(ParkEntity park, StationEntity station);
}
