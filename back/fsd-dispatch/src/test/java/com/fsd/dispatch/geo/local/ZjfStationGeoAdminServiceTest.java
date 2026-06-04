package com.fsd.dispatch.geo.local;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.StationEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZjfStationGeoAdminServiceTest {

    private ZjfStationGeoAdminService service;

    @BeforeEach
    void setUp() {
        StationCoordinateValidator validator = OsmPilotGeoTestSupport.stationValidator();
        LocalPilotRoadGraphService graph = OsmPilotGeoTestSupport.graph();
        service = new ZjfStationGeoAdminService(new StationRoadSnapService(graph), validator);
    }

    @Test
    void shouldSnapAndAcceptV30PickStation() {
        StationEntity station = zjfStation("ZJF-PICK-01", 121.074453, 31.960396);
        service.snapAndValidate(station);
        assertTrue(station.getCoordLng().doubleValue() >= 121.072051);
    }

    @Test
    void shouldRejectCoordinateFarFromRoad() {
        StationEntity station = zjfStation("ZJF-DROP-99", 121.088600, 31.963000);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.snapAndValidate(station));
        assertEquals("STATION_COORD_INVALID", ex.getCode());
    }

    @Test
    void shouldSkipNonZjfStation() {
        StationEntity station = new StationEntity();
        station.setArea("A");
        station.setStationCode("A1");
        station.setCoordLng(BigDecimal.valueOf(121.086500));
        station.setCoordLat(BigDecimal.valueOf(31.963900));
        service.snapAndValidate(station);
        assertEquals(121.086500, station.getCoordLng().doubleValue(), 0.0001);
    }

    private static StationEntity zjfStation(String code, double lng, double lat) {
        StationEntity station = new StationEntity();
        station.setArea("ZJF");
        station.setStationCode(code);
        station.setCoordLng(BigDecimal.valueOf(lng));
        station.setCoordLat(BigDecimal.valueOf(lat));
        return station;
    }
}
