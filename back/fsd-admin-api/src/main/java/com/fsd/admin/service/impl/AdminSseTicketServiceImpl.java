package com.fsd.admin.service.impl;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.service.AdminSseTicketService;
import com.fsd.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class AdminSseTicketServiceImpl implements AdminSseTicketService {

    private final AdminSseProperties properties;
    private final ConcurrentMap<String, TicketHolder> tickets = new ConcurrentHashMap<>();

    public AdminSseTicketServiceImpl(AdminSseProperties properties) {
        this.properties = properties;
    }

    @Override
    public String issue(AdminAuthContext context) {
        pruneExpired();
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new TicketHolder(copyContext(context),
                Instant.now().plusSeconds(properties.getTicketTtlSeconds())));
        return ticket;
    }

    @Override
    public AdminAuthContext consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException("ADMIN_SSE_TICKET_REQUIRED", "SSE ticket is required");
        }
        TicketHolder holder = tickets.remove(ticket);
        if (holder == null || holder.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE ticket is invalid or expired");
        }
        return holder.context();
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private AdminAuthContext copyContext(AdminAuthContext context) {
        return AdminAuthContext.builder()
                .userId(context.getUserId())
                .username(context.getUsername())
                .displayName(context.getDisplayName())
                .role(context.getRole())
                .build();
    }

    private record TicketHolder(AdminAuthContext context, Instant expiresAt) {
    }
}
