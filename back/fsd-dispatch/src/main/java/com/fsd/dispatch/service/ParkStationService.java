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

    ParkStationResponse toStationResponse(ParkEntity park, StationEntity station);
}
