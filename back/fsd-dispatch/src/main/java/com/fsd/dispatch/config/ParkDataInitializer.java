package com.fsd.dispatch.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.ParkStatus;
import com.fsd.common.enums.StationStatus;
import com.fsd.common.enums.StationType;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.mapper.StationMapper;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Seeds park/station rows when DB is empty (e.g. dev without running V4 SQL).
 * YAML {@code fsd.park.stations} is only used as fallback seed source.
 */
@Component
public class ParkDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ParkDataInitializer.class);

    private final ParkMapper parkMapper;
    private final StationMapper stationMapper;
    private final ParkPilotProperties parkPilotProperties;

    public ParkDataInitializer(ParkMapper parkMapper,
                               StationMapper stationMapper,
                               ParkPilotProperties parkPilotProperties) {
        this.parkMapper = parkMapper;
        this.stationMapper = stationMapper;
        this.parkPilotProperties = parkPilotProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long parkCount;
        try {
            parkCount = parkMapper.selectCount(new LambdaQueryWrapper<ParkEntity>()
                    .eq(ParkEntity::getDeleted, 0));
        } catch (DataAccessException ex) {
            log.debug("Skip park data initializer because t_park is not ready: {}", ex.getMessage());
            return;
        }
        if (parkCount != null && parkCount > 0) {
            return;
        }
        List<ParkPilotProperties.StationConfig> yamlStations = parkPilotProperties.getStations();
        if (yamlStations == null || yamlStations.isEmpty()) {
            log.warn("No parks in database and no YAML stations to seed; park APIs will fail until V4 SQL is applied");
            return;
        }

        ParkEntity park = new ParkEntity();
        park.setParkCode(parkPilotProperties.getDefaultParkCode());
        park.setParkName("默认示范园区");
        park.setMapWidth(parkPilotProperties.getWidth());
        park.setMapHeight(parkPilotProperties.getHeight());
        park.setMinZoom(parkPilotProperties.getMinZoom());
        park.setMaxZoom(parkPilotProperties.getMaxZoom());
        park.setVehicleSpeedPxPerSecond(parkPilotProperties.getVehicleSpeedPxPerSecond());
        park.setStatus(ParkStatus.ACTIVE.name());
        park.setDefaultFlag(1);
        park.setVersion(0);
        park.setDeleted(0);
        parkMapper.insert(park);

        int sort = 0;
        for (ParkPilotProperties.StationConfig config : yamlStations) {
            StationEntity station = new StationEntity();
            if (config.getId() != null) {
                station.setId(config.getId());
            }
            station.setParkId(park.getId());
            station.setStationCode(config.getCode());
            station.setStationName(config.getName());
            station.setStationType(resolveStationType(config.getArea()));
            station.setCoordX(config.getX());
            station.setCoordY(config.getY());
            station.setArea(config.getArea());
            station.setStatus(StationStatus.ACTIVE.name());
            station.setSortOrder(++sort);
            station.setVersion(0);
            station.setDeleted(0);
            stationMapper.insert(station);
        }
        log.info("Seeded park {} with {} stations from YAML fallback", park.getParkCode(), yamlStations.size());
    }

    private String resolveStationType(String area) {
        if (area != null && area.startsWith("B")) {
            return StationType.DROPOFF.name();
        }
        if (area != null && area.startsWith("A")) {
            return StationType.PICKUP.name();
        }
        return StationType.GENERAL.name();
    }
}
