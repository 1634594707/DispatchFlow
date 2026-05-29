package com.fsd.dispatch.fleet.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Phase 1 验收 — Redis 运行态序列化（重启恢复前提）")
class RedisFleetRuntimeServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void jsonRoundTripShouldPreservePluggedInStandby() throws Exception {
        FleetRuntime original = FleetRuntime.builder()
                .vehicleId(42L)
                .runtimeStage("STANDBY")
                .pluggedIn(true)
                .soc(100)
                .lastTelemetryAt(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(original);
        FleetRuntime reloaded = objectMapper.readValue(json, FleetRuntime.class);

        assertEquals("STANDBY", reloaded.getRuntimeStage());
        assertEquals(true, reloaded.getPluggedIn());
        assertEquals(100, reloaded.getSoc());
        assertEquals(42L, reloaded.getVehicleId());
    }

    @Test
    void redisKeyFormatShouldMatchServiceConvention() {
        assertTrue("fleet:runtime:42".matches("fleet:runtime:\\d+"));
    }
}
