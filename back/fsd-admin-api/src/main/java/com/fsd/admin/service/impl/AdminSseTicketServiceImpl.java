package com.fsd.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.service.AdminSseTicketService;
import com.fsd.admin.metrics.AdminSseMetrics;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AdminSseTicketServiceImpl implements AdminSseTicketService {

    private static final String KEY_PREFIX = "fsd:admin:sse-ticket:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]) "
                    + "if value then redis.call('del', KEYS[1]) end "
                    + "return value", String.class);

    private final AdminSseProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminSseMetrics metrics;

    public AdminSseTicketServiceImpl(AdminSseProperties properties,
                                     StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper,
                                     AdminSseMetrics metrics) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public String issue(AdminAuthContext context) {
        if (context == null) {
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE auth context is required");
        }
        long ttlSeconds = properties.getTicketTtlSeconds();
        if (ttlSeconds <= 0) {
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE ticket TTL must be positive");
        }
        String ticket = UUID.randomUUID().toString();
        try {
            String payload = objectMapper.writeValueAsString(TicketPayload.from(context));
            stringRedisTemplate.opsForValue().set(
                    buildKey(ticket), payload, Duration.ofSeconds(ttlSeconds));
            metrics.ticketIssued();
            return ticket;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("ADMIN_SSE_TICKET_SERIALIZE_FAILED", "Unable to serialize SSE ticket");
        } catch (RuntimeException ex) {
            throw new BusinessException("ADMIN_SSE_TICKET_STORE_FAILED", "Unable to store SSE ticket");
        }
    }

    @Override
    public AdminAuthContext consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            metrics.ticketInvalid();
            throw new BusinessException("ADMIN_SSE_TICKET_REQUIRED", "SSE ticket is required");
        }
        String payload;
        try {
            payload = stringRedisTemplate.execute(
                    CONSUME_SCRIPT, Collections.singletonList(buildKey(ticket)));
        } catch (RuntimeException ex) {
            metrics.ticketInvalid();
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE ticket is invalid or expired");
        }
        if (payload == null || payload.isBlank()) {
            metrics.ticketInvalid();
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE ticket is invalid or expired");
        }
        try {
            AdminAuthContext context = objectMapper.readValue(payload, TicketPayload.class).toContext();
            metrics.ticketConsumed();
            return context;
        } catch (JsonProcessingException | RuntimeException ex) {
            metrics.ticketInvalid();
            throw new BusinessException("ADMIN_SSE_TICKET_INVALID", "SSE ticket is invalid or expired");
        }
    }

    private String buildKey(String ticket) {
        return KEY_PREFIX + ticket;
    }

    private record TicketPayload(Long userId, String username, String displayName, AdminRole role) {

        private static TicketPayload from(AdminAuthContext context) {
            return new TicketPayload(
                    context.getUserId(), context.getUsername(), context.getDisplayName(), context.getRole());
        }

        private AdminAuthContext toContext() {
            return AdminAuthContext.builder()
                    .userId(userId)
                    .username(username)
                    .displayName(displayName)
                    .role(role)
                    .build();
        }
    }
}
