package com.fsd.dispatch.event.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchEventOutboxServiceImpl implements DispatchEventOutboxService {

    private final DispatchEventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public DispatchEventOutboxServiceImpl(DispatchEventOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
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
        entity.setStatus("PUBLISHED");
        entity.setLastError(null);
        entity.setNextRetryTime(null);
        outboxMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void markFailed(String eventId, String lastError) {
        DispatchEventOutboxEntity entity = findByEventId(eventId);
        int retryCount = entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1;
        entity.setStatus("FAILED");
        entity.setRetryCount(retryCount);
        entity.setLastError(truncate(lastError));
        entity.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(retryCount * 30L, 300L)));
        outboxMapper.updateById(entity);
    }

    @Override
    public List<DispatchEventOutboxEntity> listRetryableEvents(int limit) {
        return outboxMapper.selectList(new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .in(DispatchEventOutboxEntity::getStatus, List.of("PENDING", "FAILED"))
                .le(DispatchEventOutboxEntity::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(DispatchEventOutboxEntity::getCreatedAt)
                .last("limit " + limit));
    }

    @Override
    public DispatchDomainEvent rebuildDomainEvent(DispatchEventOutboxEntity entity) {
        return DispatchDomainEvent.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .eventTime(entity.getCreatedAt())
                .businessKey(entity.getBusinessKey())
                .payload(readPayload(entity.getPayload()))
                .build();
    }

    private DispatchEventOutboxEntity findByEventId(String eventId) {
        DispatchEventOutboxEntity entity = outboxMapper.selectOne(new LambdaQueryWrapper<DispatchEventOutboxEntity>()
                .eq(DispatchEventOutboxEntity::getEventId, eventId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("DISPATCH_EVENT_NOT_FOUND", "Dispatch event outbox record not found");
        }
        return entity;
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
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {
            });
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
