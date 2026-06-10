package com.fsd.admin.controller;

import com.fsd.admin.service.FleetTelemetryStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/fleet")
@Tag(name = "Fleet Telemetry Stream", description = "SSE stream for live vehicle positions on the tracking map")
public class FleetTelemetryStreamController {

    private final FleetTelemetryStreamService streamService;

    public FleetTelemetryStreamController(FleetTelemetryStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Fleet telemetry SSE stream", description = "Live vehicle positions and sensor data. Auth via `token` query param.")
    public SseEmitter streamTelemetry(@RequestParam(required = false) Long parkId) {
        return streamService.createStream(parkId);
    }
}
