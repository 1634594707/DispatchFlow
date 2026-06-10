package com.fsd.admin.controller;

import com.fsd.admin.service.AdminDispatchStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/dispatch")
@Tag(name = "Dispatch Stream", description = "SSE stream for dashboard, workbench, and exception alerts")
public class AdminDispatchStreamController {

    private final AdminDispatchStreamService streamService;

    public AdminDispatchStreamController(AdminDispatchStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Dispatch SSE stream", description = "Real-time push for dashboard, workbench, and exception alerts. Auth via `token` query param.")
    public SseEmitter stream() {
        return streamService.createStream();
    }
}
