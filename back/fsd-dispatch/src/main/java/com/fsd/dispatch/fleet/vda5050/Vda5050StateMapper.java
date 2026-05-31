package com.fsd.dispatch.fleet.vda5050;

import com.fasterxml.jackson.databind.JsonNode;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class Vda5050StateMapper {

    private Vda5050StateMapper() {
    }

    public static VehicleTelemetryRequest toTelemetry(VehicleEntity vehicle, JsonNode state, long fallbackSeq) {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setVehicleCode(vehicle.getVehicleCode());
        request.setRuntimeStage(resolveRuntimeStage(state));
        request.setPluggedIn(resolvePluggedIn(state));
        request.setTargetCode(readText(state, "lastNodeId"));
        request.setTargetType("VDA5050_NODE");
        request.setSoc(resolveSoc(state));
        request.setX(resolveCoordinate(state, "x"));
        request.setY(resolveCoordinate(state, "y"));
        request.setReportTime(resolveReportTime(state));
        request.setEventSeq(resolveEventSeq(state, fallbackSeq));
        return request;
    }

    static String resolveRuntimeStage(JsonNode state) {
        JsonNode battery = state.path("batteryState");
        if (battery.path("charging").asBoolean(false)) {
            return "CHARGING";
        }
        if (state.path("driving").asBoolean(false)) {
            return "EN_ROUTE";
        }
        if (state.path("paused").asBoolean(false)) {
            return "PAUSED";
        }
        return "STANDBY";
    }

    static boolean resolvePluggedIn(JsonNode state) {
        return state.path("batteryState").path("charging").asBoolean(false);
    }

    static int resolveSoc(JsonNode state) {
        JsonNode charge = state.path("batteryState").path("batteryCharge");
        if (charge.isMissingNode() || charge.isNull()) {
            return 0;
        }
        double ratio = charge.asDouble(0);
        if (ratio <= 1.0) {
            return (int) Math.round(ratio * 100);
        }
        return (int) Math.round(Math.min(ratio, 100));
    }

    static BigDecimal resolveCoordinate(JsonNode state, String axis) {
        JsonNode position = state.path("agvPosition");
        if (position.isMissingNode() || position.isNull()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(position.path(axis).asDouble(0)).setScale(3, RoundingMode.HALF_UP);
    }

    static LocalDateTime resolveReportTime(JsonNode state) {
        String timestamp = readText(state, "timestamp");
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneId.systemDefault());
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    static long resolveEventSeq(JsonNode state, long fallbackSeq) {
        if (state.hasNonNull("headerId")) {
            return state.path("headerId").asLong(fallbackSeq);
        }
        return fallbackSeq;
    }

    private static String readText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }
}
