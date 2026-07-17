package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.admin.service.AdminSseTicketService;
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
@RequestMapping("/api/admin/dispatch")
@Tag(name = "Dispatch Stream", description = "SSE stream for dashboard, workbench, and exception alerts")
public class AdminDispatchStreamController {

    private final AdminDispatchStreamService streamService;
    private final AdminSseTicketService ticketService;

    public AdminDispatchStreamController(AdminDispatchStreamService streamService,
                                         AdminSseTicketService ticketService) {
        this.streamService = streamService;
        this.ticketService = ticketService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SecurityRequirement(name = "")
    @Operation(summary = "Dispatch SSE stream", description = "Real-time push for dashboard, workbench, and exception alerts. Auth via `ticket` query param.")
    public SseEmitter stream(@RequestParam String ticket) {
        AdminAuthContext context = ticketService.consume(ticket);
        return streamService.createStream(context, null);
    }
}
