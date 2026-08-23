package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.metrics.AdminSseMetrics;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AdminSseTicketServiceImplTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private AdminSseProperties properties;
    private AdminSseTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper();
        properties = new AdminSseProperties();
        service = new AdminSseTicketServiceImpl(properties, redis, objectMapper,
                new AdminSseMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void issueShouldStoreJsonWithConfiguredTtl() {
        String ticket = service.issue(context());

        verify(valueOperations).set(anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60L)));
        assertNotNull(ticket);
    }

    @Test
    void ticketShouldBeSingleUse() {
        String payload = "{\"userId\":1,\"username\":\"admin\","
                + "\"displayName\":\"Admin\",\"role\":\"ADMIN\"}";
        mockExecuteResults(payload, null);

        assertEquals("admin", service.consume("ticket-1").getUsername());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.consume("ticket-1"));
        assertEquals("ADMIN_SSE_TICKET_INVALID", ex.getCode());
    }

    @Test
    void missingTicketShouldBeRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.consume(" "));
        assertEquals("ADMIN_SSE_TICKET_REQUIRED", ex.getCode());
    }

    @Test
    void malformedPayloadShouldBeRejected() {
        mockExecuteResults("not-json");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.consume("ticket-1"));
        assertEquals("ADMIN_SSE_TICKET_INVALID", ex.getCode());
    }

    @Test
    void nonPositiveTtlShouldNotIssueTicket() {
        properties.setTicketTtlSeconds(0L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issue(context()));
        assertEquals("ADMIN_SSE_TICKET_INVALID", ex.getCode());
    }

    private void mockExecuteResults(String... results) {
        AtomicInteger index = new AtomicInteger();
        redis = mock(StringRedisTemplate.class, invocation -> {
            if ("execute".equals(invocation.getMethod().getName())
                    && invocation.getArguments().length > 0
                    && invocation.getArguments()[0] instanceof RedisScript<?>) {
                int resultIndex = index.getAndIncrement();
                return resultIndex < results.length ? results[resultIndex] : null;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        service = new AdminSseTicketServiceImpl(properties, redis, objectMapper,
                new AdminSseMetrics(new SimpleMeterRegistry()));
    }

    private AdminAuthContext context() {
        return AdminAuthContext.builder()
                .userId(1L)
                .username("admin")
                .displayName("Admin")
                .role(AdminRole.ADMIN)
                .build();
    }
}
