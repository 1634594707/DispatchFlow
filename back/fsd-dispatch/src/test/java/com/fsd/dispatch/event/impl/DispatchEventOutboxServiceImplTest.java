package com.fsd.dispatch.event.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.DispatchOutboxProperties;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
import com.fsd.dispatch.metrics.DispatchOutboxMetrics;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DispatchEventOutboxServiceImplTest {

    @Mock
    private DispatchEventOutboxMapper outboxMapper;

    private DispatchOutboxProperties properties;
    private DispatchEventOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new DispatchOutboxProperties();
        properties.setMaxRetries(3);
        service = new DispatchEventOutboxServiceImpl(outboxMapper, new ObjectMapper(), properties,
                new DispatchOutboxMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void markFailedShouldMoveToDeadLetterAtRetryLimit() {
        DispatchEventOutboxEntity entity = event("evt-1", 2);
        when(outboxMapper.selectPage(any(Page.class), any())).thenReturn(page(entity));

        service.markFailed("evt-1", "broker unavailable");

        ArgumentCaptor<DispatchEventOutboxEntity> captor = ArgumentCaptor.forClass(DispatchEventOutboxEntity.class);
        verify(outboxMapper).updateById(captor.capture());
        DispatchEventOutboxEntity updated = captor.getValue();
        assertEquals("DEAD_LETTER", updated.getStatus());
        assertEquals(3, updated.getRetryCount());
        assertEquals(null, updated.getNextRetryTime());
        assertEquals("broker unavailable", updated.getLastError());
    }

    @Test
    void markFailedBeforeRetryLimitShouldRemainRetryable() {
        DispatchEventOutboxEntity entity = event("evt-1", 1);
        when(outboxMapper.selectPage(any(Page.class), any())).thenReturn(page(entity));

        service.markFailed("evt-1", "temporary failure");

        ArgumentCaptor<DispatchEventOutboxEntity> captor = ArgumentCaptor.forClass(DispatchEventOutboxEntity.class);
        verify(outboxMapper).updateById(captor.capture());
        DispatchEventOutboxEntity updated = captor.getValue();
        assertEquals("FAILED", updated.getStatus());
        assertEquals(2, updated.getRetryCount());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getNextRetryTime());
    }

    @Test
    void listDeadLetterEventsShouldFilterAndOrderByUpdatedTime() {
        DispatchEventOutboxEntity entity = event("evt-1", 3);
        entity.setStatus("DEAD_LETTER");
        when(outboxMapper.selectPage(any(Page.class), any())).thenReturn(page(entity));

        assertEquals(List.of(entity), service.listDeadLetterEvents(20));
        verify(outboxMapper).selectPage(any(Page.class), any());
    }

    @Test
    void claimRetryableEventsShouldAssignProcessingLease() {
        DispatchEventOutboxEntity entity = event("evt-1", 0);
        entity.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        when(outboxMapper.selectPage(any(Page.class), any())).thenReturn(page(entity));
        when(outboxMapper.claimIfAvailable(any(), any(), any(), any(), any())).thenReturn(1);

        List<DispatchEventOutboxEntity> claimed = service.claimRetryableEvents(10);

        assertEquals(1, claimed.size());
        assertEquals("PROCESSING", claimed.getFirst().getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(claimed.getFirst().getClaimToken());
        org.junit.jupiter.api.Assertions.assertNotNull(claimed.getFirst().getLeaseUntil());
        verify(outboxMapper).claimIfAvailable(any(), any(), any(), any(), any());
    }

    @Test
    void rebuildDomainEventShouldCarryClaimToken() {
        DispatchEventOutboxEntity entity = event("evt-1", 1);
        entity.setPayload("{\"parkId\":9}");
        entity.setClaimToken("lease-1");

        var rebuilt = service.rebuildDomainEvent(entity);

        assertEquals("lease-1", rebuilt.getOutboxClaimToken());
        assertEquals(9L, rebuilt.getParkId());
    }

    @Test
    void claimedFailureShouldUseTokenGuardedUpdate() {
        DispatchEventOutboxEntity entity = event("evt-1", 1);
        entity.setStatus("PROCESSING");
        entity.setClaimToken("lease-1");
        when(outboxMapper.selectPage(any(Page.class), any())).thenReturn(page(entity));

        service.markFailed("evt-1", "temporary failure", "lease-1");

        verify(outboxMapper).markClaimFailed(
                org.mockito.ArgumentMatchers.eq("evt-1"),
                org.mockito.ArgumentMatchers.eq("lease-1"),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq("temporary failure"),
                any());
    }

    @Test
    void claimEventShouldReturnTokenOnlyWhenDatabaseClaimSucceeds() {
        when(outboxMapper.claimEventIfAvailable(any(), any(), any(), any(), any())).thenReturn(1, 0);

        org.junit.jupiter.api.Assertions.assertNotNull(service.claimEvent("evt-1"));
        org.junit.jupiter.api.Assertions.assertNull(service.claimEvent("evt-1"));
    }

    private Page<DispatchEventOutboxEntity> page(DispatchEventOutboxEntity entity) {
        Page<DispatchEventOutboxEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(entity));
        return page;
    }

    private DispatchEventOutboxEntity event(String eventId, int retryCount) {
        DispatchEventOutboxEntity entity = new DispatchEventOutboxEntity();
        entity.setId(1L);
        entity.setEventId(eventId);
        entity.setRetryCount(retryCount);
        entity.setStatus("FAILED");
        entity.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        entity.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return entity;
    }
}
