package com.fsd.admin.controller;

import com.fsd.admin.service.AdminSseTicketService;
import com.fsd.admin.service.FleetTelemetryStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    private final AdminSseTicketService ticketService;

    public FleetTelemetryStreamController(FleetTelemetryStreamService streamService,
                                          AdminSseTicketService ticketService) {
        this.streamService = streamService;
        this.ticketService = ticketService;
    }

    @GetMapping(value = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SecurityRequirement(name = "")
    @Operation(summary = "Fleet telemetry SSE stream", description = "Live vehicle positions and sensor data. Auth via `ticket` query param.")
    public SseEmitter streamTelemetry(@RequestParam String ticket,
                                      @RequestParam(required = false) Long parkId) {
        ticketService.consume(ticket);
        return streamService.createStream(parkId);
    }
}
