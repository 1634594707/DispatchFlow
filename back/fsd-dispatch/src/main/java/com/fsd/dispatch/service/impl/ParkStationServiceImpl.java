package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.ParkStatus;
import com.fsd.common.enums.StationStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ParkStationServiceImpl implements ParkStationService {

    private final ParkMapper parkMapper;
    private final StationMapper stationMapper;
    private final ParkPilotProperties parkPilotProperties;

    public ParkStationServiceImpl(ParkMapper parkMapper,
                                  StationMapper stationMapper,
                                  ParkPilotProperties parkPilotProperties) {
        this.parkMapper = parkMapper;
        this.stationMapper = stationMapper;
        this.parkPilotProperties = parkPilotProperties;
    }

    @Override
    public ParkEntity requireDefaultPark() {
        ParkEntity park = parkMapper.selectOne(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getDefaultFlag, 1)
                .eq(ParkEntity::getStatus, ParkStatus.ACTIVE.name())
                .eq(ParkEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (park != null) {
            return park;
        }
        String defaultCode = parkPilotProperties.getDefaultParkCode();
        if (defaultCode != null && !defaultCode.isBlank()) {
            park = parkMapper.selectOne(new LambdaQueryWrapper<ParkEntity>()
                    .eq(ParkEntity::getParkCode, defaultCode)
                    .eq(ParkEntity::getDeleted, 0)
                    .last("LIMIT 1"));
            if (park != null) {
                return park;
            }
        }
        throw new BusinessException("PARK_NOT_FOUND", "Default park not configured");
    }

    @Override
    public ParkEntity requirePark(Long parkId) {
        ParkEntity park = parkMapper.selectOne(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getId, parkId)
                .eq(ParkEntity::getDeleted, 0));
        if (park == null || !ParkStatus.ACTIVE.name().equals(park.getStatus())) {
            throw new BusinessException("PARK_NOT_FOUND", "Park not found or inactive");
        }
        return park;
    }

    @Override
    public ParkEntity requireParkByCode(String parkCode) {
        ParkEntity park = parkMapper.selectOne(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getParkCode, parkCode)
                .eq(ParkEntity::getDeleted, 0));
        if (park == null || !ParkStatus.ACTIVE.name().equals(park.getStatus())) {
            throw new BusinessException("PARK_NOT_FOUND", "Park not found or inactive");
        }
        return park;
    }

    @Override
    public List<ParkResponse> listActiveParks() {
        return parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>()
                        .eq(ParkEntity::getStatus, ParkStatus.ACTIVE.name())
                        .eq(ParkEntity::getDeleted, 0)
                        .orderByDesc(ParkEntity::getDefaultFlag)
                        .orderByAsc(ParkEntity::getId))
                .stream()
                .map(this::toParkResponse)
                .toList();
    }

    @Override
    public List<ParkStationResponse> listStations(Long parkId) {
        ParkEntity park = requirePark(parkId);
        return listActiveStations(park).stream()
                .map(station -> toStationResponse(park, station))
                .toList();
    }

    @Override
    public ParkStationResponse requireStation(Long stationId) {
        StationEntity station = stationMapper.selectOne(new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getId, stationId)
                .eq(StationEntity::getDeleted, 0));
        if (station == null || !StationStatus.ACTIVE.name().equals(station.getStatus())) {
            throw new BusinessException("PARK_STATION_NOT_FOUND", "Park station not found");
        }
        ParkEntity park = requirePark(station.getParkId());
        return toStationResponse(park, station);
    }

    @Override
    public void assertStationsBelongToSamePark(Long pickupStationId, Long dropoffStationId) {
        StationEntity pickup = requireStationEntity(pickupStationId);
        StationEntity dropoff = requireStationEntity(dropoffStationId);
        if (!Objects.equals(pickup.getParkId(), dropoff.getParkId())) {
            throw new BusinessException("PARK_STATION_CROSS_PARK", "Pickup and dropoff must be in the same park");
        }
    }

    @Override
    public void assertStationInPark(Long stationId, Long parkId) {
        StationEntity station = requireStationEntity(stationId);
        if (!Objects.equals(station.getParkId(), parkId)) {
            throw new BusinessException("PARK_STATION_MISMATCH", "Station does not belong to the selected park");
        }
    }

    @Override
    public ParkStationResponse toStationResponse(ParkEntity park, StationEntity station) {
        return ParkStationResponse.builder()
                .parkId(park.getId())
                .parkCode(park.getParkCode())
                .stationId(station.getId())
                .stationCode(station.getStationCode())
                .stationName(station.getStationName())
                .stationType(station.getStationType())
                .x(station.getCoordX())
                .y(station.getCoordY())
                .area(station.getArea())
                .build();
    }

    private StationEntity requireStationEntity(Long stationId) {
        StationEntity station = stationMapper.selectOne(new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getId, stationId)
                .eq(StationEntity::getDeleted, 0));
        if (station == null || !StationStatus.ACTIVE.name().equals(station.getStatus())) {
            throw new BusinessException("PARK_STATION_NOT_FOUND", "Park station not found");
        }
        return station;
    }

    private List<StationEntity> listActiveStations(ParkEntity park) {
        return stationMapper.selectList(new LambdaQueryWrapper<StationEntity>()
                        .eq(StationEntity::getParkId, park.getId())
                        .eq(StationEntity::getStatus, StationStatus.ACTIVE.name())
                        .eq(StationEntity::getDeleted, 0)
                        .orderByAsc(StationEntity::getSortOrder)
                        .orderByAsc(StationEntity::getId))
                .stream()
                .sorted(Comparator.comparing(StationEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(StationEntity::getId))
                .toList();
    }

    private ParkResponse toParkResponse(ParkEntity park) {
        return ParkResponse.builder()
                .parkId(park.getId())
                .parkCode(park.getParkCode())
                .parkName(park.getParkName())
                .mapWidth(park.getMapWidth())
                .mapHeight(park.getMapHeight())
                .defaultPark(park.getDefaultFlag() != null && park.getDefaultFlag() == 1)
                .build();
    }
}
