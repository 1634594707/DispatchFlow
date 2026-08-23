package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.vo.AdminDispatchDeadLetterResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dispatch/outbox")
@Tag(name = "Dispatch Outbox", description = "Dispatch event delivery failure inspection")
@SecurityRequirement(name = "adminToken")
public class AdminDispatchOutboxController {

    private final DispatchEventOutboxService outboxService;

    public AdminDispatchOutboxController(DispatchEventOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @GetMapping("/dead-letters")
    @Operation(summary = "List dispatch event dead letters")
    public ApiResponse<List<AdminDispatchDeadLetterResponse>> listDeadLetters(
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.success(outboxService.listDeadLetterEvents(boundedLimit).stream()
                .map(this::toResponse)
                .toList());
    }

    private AdminDispatchDeadLetterResponse toResponse(DispatchEventOutboxEntity entity) {
        return AdminDispatchDeadLetterResponse.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .businessKey(entity.getBusinessKey())
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
