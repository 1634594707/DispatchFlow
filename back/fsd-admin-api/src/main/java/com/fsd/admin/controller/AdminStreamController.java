package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.admin.service.AdminSseTicketService;
import com.fsd.admin.vo.AdminSseTicketResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Streams", description = "Admin SSE ticket and stream endpoints")
@SecurityRequirement(name = "adminToken")
public class AdminStreamController {

    private final AdminDispatchStreamService dispatchStreamService;
    private final AdminSseTicketService ticketService;
    private final AdminSseProperties properties;

    public AdminStreamController(AdminDispatchStreamService dispatchStreamService,
                                 AdminSseTicketService ticketService,
                                 AdminSseProperties properties) {
        this.dispatchStreamService = dispatchStreamService;
        this.ticketService = ticketService;
        this.properties = properties;
    }

    @PostMapping("/sse-ticket")
    @Operation(summary = "Issue a short-lived SSE ticket")
    public ApiResponse<AdminSseTicketResponse> issueTicket(HttpServletRequest request) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(AdminSseTicketResponse.builder()
                .ticket(ticketService.issue(context))
                .expiresInSeconds(properties.getTicketTtlSeconds())
                .build());
    }

    @GetMapping({"/dispatch/stream", "/fleet/telemetry/stream"})
    @SecurityRequirement(name = "")
    @Operation(summary = "Open admin SSE stream with a short-lived ticket")
    public SseEmitter stream(@RequestParam(required = false) String ticket,
                             @RequestParam(required = false) Long parkId) {
        // 拦截器对 SSE 路径放行（EventSource 无法设置 Header），此处强制校验 ticket。
        // 缺失/无效 ticket 由 AdminSseTicketService.consume 抛 BusinessException，
        // GlobalExceptionHandler 映射为 401。
        AdminAuthContext context = ticketService.consume(ticket);
        return dispatchStreamService.createStream(context, parkId);
    }
}
