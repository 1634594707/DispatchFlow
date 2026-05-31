package com.fsd.dispatch.fleet.vda5050;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;

class Vda5050StateMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapDrivingStateToEnRoute() throws Exception {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("headerId", 42);
        state.put("timestamp", "2026-05-31T10:00:00Z");
        state.put("driving", true);
        state.putObject("batteryState").put("batteryCharge", 0.75);
        ObjectNode position = state.putObject("agvPosition");
        position.put("x", 120.5);
        position.put("y", 220.25);

        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setVehicleCode("VDA5050-001");

        VehicleTelemetryRequest request = Vda5050StateMapper.toTelemetry(vehicle, state, 1L);

        assertEquals("EN_ROUTE", request.getRuntimeStage());
        assertEquals(75, request.getSoc());
        assertEquals(42L, request.getEventSeq());
        assertNotNull(request.getReportTime());
    }

    @Test
    void shouldMapChargingState() throws Exception {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("driving", false);
        ObjectNode battery = state.putObject("batteryState");
        battery.put("charging", true);
        battery.put("batteryCharge", 0.4);
        state.putObject("agvPosition").put("x", 0).put("y", 0);

        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setVehicleCode("VDA5050-001");

        VehicleTelemetryRequest request = Vda5050StateMapper.toTelemetry(vehicle, state, 9L);

        assertEquals("CHARGING", request.getRuntimeStage());
        assertEquals(true, request.getPluggedIn());
        assertEquals(40, request.getSoc());
    }
}
