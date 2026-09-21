package com.fsd.dispatch.fleet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;

/** §7.6 之后只剩地理池：这条测试钉住"示意池不会再被捞回来"。 */
class PilotFleetSupportTest {

    @Test
    void onlyGeoPilotVehiclesAreAssignable() {
        assertTrue(PilotFleetSupport.matchesOrderFleet(vehicle("ZJF-AV-01")));
        assertFalse(PilotFleetSupport.matchesOrderFleet(vehicle("PARK-01")));
        assertFalse(PilotFleetSupport.matchesOrderFleet(vehicle("FMS-REAL-01")));
        assertFalse(PilotFleetSupport.matchesOrderFleet(null));
    }

    @Test
    void schematicPoolHelpersAreGone() {
        assertFalse(PilotFleetSupport.isPilotSimVehicleCode("PARK-01"));
        assertTrue(PilotFleetSupport.isPilotSimVehicleCode("ZJF-AV-07"));
    }

    @Test
    void zjfStationsAndFallbackAreasAreClassified() {
        assertTrue(PilotFleetSupport.isGeoDeliveryStation(station("ZJF-PICK-01", null)));
        assertTrue(PilotFleetSupport.isGeoDeliveryStation(station("ANYTHING", "ZJF")));
        assertFalse(PilotFleetSupport.isGeoDeliveryStation(station("A1", "A")));
        assertFalse(PilotFleetSupport.isGeoDeliveryStation(null));
    }

    private static VehicleEntity vehicle(String code) {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setVehicleCode(code);
        vehicle.setLinkMode("SIM");
        return vehicle;
    }

    private static ParkStationResponse station(String code, String area) {
        return ParkStationResponse.builder()
                .stationCode(code)
                .area(area)
                .build();
    }
}
