package com.fsd.dispatch.event.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.config.DispatchOutboxProperties;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
import com.fsd.dispatch.metrics.DispatchOutboxMetrics;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchEventOutboxServiceImpl implements DispatchEventOutboxService {

    private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final DispatchEventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final DispatchOutboxProperties properties;
    private final DispatchOutboxMetrics metrics;

    public DispatchEventOutboxServiceImpl(DispatchEventOutboxMapper outboxMapper,
                                          ObjectMapper objectMapper,
                                          DispatchOutboxProperties properties,
                                          DispatchOutboxMetrics metrics) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void savePending(DispatchDomainEvent event) {
        DispatchEventOutboxEntity entity = new DispatchEventOutboxEntity();
        entity.setEventId(event.getEventId());
        entity.setEventType(event.getEventType());
        entity.setBusinessKey(event.getBusinessKey());
        entity.setPayload(writePayload(event.getPayload()));
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setNextRetryTime(LocalDateTime.now());
        outboxMapper.insert(entity);
    }

    @Override
    @Transactional
    public void markPublished(String eventId) {
        DispatchEventOutboxEntity entity = findByEventId(eventId);
        markPublished(entity);
    }

    @Override
    @Transactional
    public void markPublished(String eventId, String claimToken) {
        if (claimToken == null || claimToken.isBlank()) {
            markPublished(eventId);
            return;
        }
        if (outboxMapper.markClaimPublished(eventId, claimToken) == 1) {
            metrics.published();
        }
    }

    private void markPublished(DispatchEventOutboxEntity entity) {
        entity.setStatus("PUBLISHED");
        entity.setLastError(null);
        entity.setNextRetryTime(null);
        clearClaim(entity);
        outboxMapper.updateById(entity);
        metrics.published();
    }

    @Override
    @Transactional
    public void markFailed(String eventId, String lastError) {
        DispatchEventOutboxEntity entity = findByEventId(eventId);
        markFailed(entity, lastError);
    }

    @Override
    @Transactional
    public void markFailed(String eventId, String lastError, String claimToken) {
        if (claimToken == null || claimToken.isBlank()) {
            markFailed(eventId, lastError);
            return;
        }
        DispatchEventOutboxEntity entity = findByEventIdAndClaimToken(eventId, claimToken);
        if (entity != null) {
            int retryCount = entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1;
            int maxRetries = Math.max(1, properties.getMaxRetries());
            boolean deadLetter = retryCount >= maxRetries;
            int updated = outboxMapper.markClaimFailed(
                    eventId,
                    claimToken,
                    deadLetter ? "DEAD_LETTER" : "FAILED",
                    retryCount,
                    truncate(lastError),
                    deadLetter ? null : LocalDateTime.now().plusSeconds(Math.min(retryCount * 30L, 300L)));
            if (updated == 1) {
                metrics.failed();
                if (deadLetter) {
                    metrics.deadLettered();
                }
            }
        }
    }

    private void markFailed(DispatchEventOutboxEntity entity, String lastError) {
        int retryCount = entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1;
        int maxRetries = Math.max(1, properties.getMaxRetries());
        boolean deadLetter = retryCount >= maxRetries;
        entity.setStatus(deadLetter ? "DEAD_LETTER" : "FAILED");
        entity.setRetryCount(retryCount);
        entity.setLastError(truncate(lastError));
        entity.setNextRetryTime(deadLetter
                ? null
                : LocalDateTime.now().plusSeconds(Math.min(retryCount * 30L, 300L)));
        clearClaim(entity);
        outboxMapper.updateById(entity);
        metrics.failed();
        if (deadLetter) {
            metrics.deadLettered();
        }
    }

    @Override
    @Transactional
    public String claimEvent(String eventId) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(Math.max(1L, properties.getLeaseSeconds()));
        int updated = outboxMapper.claimEventIfAvailable(eventId, token, now, now, leaseUntil);
        metrics.claimed(updated);
        return updated == 1 ? token : null;
    }

    @Override
    @Transactional
    public List<DispatchEventOutboxEntity> claimRetryableEvents(int limit) {
        int boundedLimit = Math.max(1, limit);
        LocalDateTime now = LocalDateTime.now();
        Page<DispatchEventOutboxEntity> page = new Page<>(1, boundedLimit);
        List<DispatchEventOutboxEntity> candidates = outboxMapper.selectPage(page,
                retryableQuery(now).orderByAsc(DispatchEventOutboxEntity::getCreatedAt))
                .getRecords();
        List<DispatchEventOutboxEntity> claimed = new ArrayList<>();
        for (DispatchEventOutboxEntity candidate : candidates) {
            String token = UUID.randomUUID().toString();
            LocalDateTime leaseUntil = now.plusSeconds(Math.max(1L, properties.getLeaseSeconds()));
            int updated = outboxMapper.claimIfAvailable(candidate.getId(), token, now, now, leaseUntil);
            if (updated == 1) {
                candidate.setStatus("PROCESSING");
                candidate.setClaimToken(token);
                candidate.setClaimedAt(now);
                candidate.setLeaseUntil(leaseUntil);
                claimed.add(candidate);
            }
        }
        metrics.claimed(claimed.size());
        return claimed;
    }

    @Override
    public List<DispatchEventOutboxEntity> listRetryableEvents(int limit) {
        Page<DispatchEventOutboxEntity> page = new Page<>(1, Math.max(1, limit));
        return outboxMapper.selectPage(page, retryableQuery(LocalDateTime.now())
                .orderByAsc(DispatchEventOutboxEntity::getCreatedAt))
                .getRecords();
    }

    private LambdaQueryWrapper<DispatchEventOutboxEntity> retryableQuery(LocalDateTime now) {
        return new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .and(wrapper -> wrapper
                        .and(retry -> retry
                                .in(DispatchEventOutboxEntity::getStatus, List.of("PENDING", "FAILED"))
                                .le(DispatchEventOutboxEntity::getNextRetryTime, now))
                        .or(retry -> retry
                                .eq(DispatchEventOutboxEntity::getStatus, "PROCESSING")
                                .le(DispatchEventOutboxEntity::getLeaseUntil, now)));
    }

    @Override
    public List<DispatchEventOutboxEntity> listDeadLetterEvents(int limit) {
        Page<DispatchEventOutboxEntity> page = new Page<>(1, Math.max(1, limit));
        return outboxMapper.selectPage(page, new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .eq(DispatchEventOutboxEntity::getStatus, "DEAD_LETTER")
                .orderByDesc(DispatchEventOutboxEntity::getUpdatedAt))
                .getRecords();
    }

    @Override
    public DispatchDomainEvent rebuildDomainEvent(DispatchEventOutboxEntity entity) {
        Map<String, Object> payload = readPayload(entity.getPayload());
        Long parkId = null;
        Object rawParkId = payload.get("parkId");
        if (rawParkId instanceof Number number) {
            parkId = number.longValue();
        }
        return DispatchDomainEvent.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .eventTime(entity.getCreatedAt())
                .businessKey(entity.getBusinessKey())
                .payload(payload)
                .parkId(parkId)
                .eventVersion(1)
                .outboxClaimToken(entity.getClaimToken())
                .build();
    }

    private DispatchEventOutboxEntity findByEventId(String eventId) {
        Page<DispatchEventOutboxEntity> page = outboxMapper.selectPage(new Page<>(1, 1, false),
                new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                        .eq(DispatchEventOutboxEntity::getEventId, eventId));
        List<DispatchEventOutboxEntity> records = page.getRecords();
        DispatchEventOutboxEntity entity = records.isEmpty() ? null : records.get(0);
        if (entity == null) {
            throw new BusinessException("DISPATCH_EVENT_NOT_FOUND", "Dispatch event outbox record not found");
        }
        return entity;
    }

    private DispatchEventOutboxEntity findByEventIdAndClaimToken(String eventId, String claimToken) {
        Page<DispatchEventOutboxEntity> page = outboxMapper.selectPage(new Page<>(1, 1, false),
                new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                        .eq(DispatchEventOutboxEntity::getEventId, eventId)
                        .eq(DispatchEventOutboxEntity::getClaimToken, claimToken)
                        .eq(DispatchEventOutboxEntity::getStatus, "PROCESSING"));
        List<DispatchEventOutboxEntity> records = page.getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    private void clearClaim(DispatchEventOutboxEntity entity) {
        entity.setClaimToken(null);
        entity.setClaimedAt(null);
        entity.setLeaseUntil(null);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("DISPATCH_EVENT_SERIALIZE_FAILED", ex.getMessage());
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("DISPATCH_EVENT_DESERIALIZE_FAILED", ex.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
