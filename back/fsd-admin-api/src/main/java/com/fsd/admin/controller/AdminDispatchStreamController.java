package com.fsd.admin.controller;

import com.fsd.admin.service.AdminDispatchStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/dispatch")
public class AdminDispatchStreamController {

    private final AdminDispatchStreamService streamService;

    public AdminDispatchStreamController(AdminDispatchStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.createStream();
    }
}
