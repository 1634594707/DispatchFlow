package com.fsd.dispatch.fleet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;

class PilotFleetSupportTest {

    @Test
    void matchesGeoOrderToGeoVehicleOnly() {
        VehicleEntity geoVehicle = vehicle("ZJF-AV-01");
        VehicleEntity parkVehicle = vehicle("PARK-01");
        ParkStationResponse pickup = station("ZJF-PICK-01", "ZJF");
        ParkStationResponse dropoff = station("ZJF-DROP-01", "ZJF");

        assertTrue(PilotFleetSupport.matchesOrderFleet(geoVehicle, pickup, dropoff));
        assertFalse(PilotFleetSupport.matchesOrderFleet(parkVehicle, pickup, dropoff));
    }

    @Test
    void matchesSchematicOrderToParkVehicleOnly() {
        VehicleEntity geoVehicle = vehicle("ZJF-AV-02");
        VehicleEntity parkVehicle = vehicle("PARK-02");
        ParkStationResponse pickup = station("A1", "A");
        ParkStationResponse dropoff = station("B1", "B");

        assertTrue(PilotFleetSupport.matchesOrderFleet(parkVehicle, pickup, dropoff));
        assertFalse(PilotFleetSupport.matchesOrderFleet(geoVehicle, pickup, dropoff));
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
