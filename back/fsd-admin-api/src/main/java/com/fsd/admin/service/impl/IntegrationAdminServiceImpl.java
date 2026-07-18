package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminWebhookUpsertRequest;
import com.fsd.admin.service.IntegrationAdminService;
import com.fsd.admin.vo.AdminExternalApiKeyResponse;
import com.fsd.admin.vo.AdminWebhookDeliveryLogResponse;
import com.fsd.admin.vo.AdminWebhookResponse;
import com.fsd.dispatch.entity.WebhookDeliveryLogEntity;
import com.fsd.dispatch.mapper.WebhookDeliveryLogMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.security.FieldEncryptionService;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import com.fsd.dispatch.mapper.WebhookSubscriptionMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationAdminServiceImpl implements IntegrationAdminService {

    private final WebhookSubscriptionMapper webhookMapper;
    private final ExternalApiKeyMapper apiKeyMapper;
    private final WebhookDeliveryLogMapper deliveryLogMapper;
    private final FieldEncryptionService fieldEncryptionService;

    public IntegrationAdminServiceImpl(WebhookSubscriptionMapper webhookMapper,
                                       ExternalApiKeyMapper apiKeyMapper,
                                       WebhookDeliveryLogMapper deliveryLogMapper,
                                       FieldEncryptionService fieldEncryptionService) {
        this.webhookMapper = webhookMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Override
    public List<AdminWebhookResponse> listWebhooks() {
        return webhookMapper.selectList(new LambdaQueryWrapper<WebhookSubscriptionEntity>()
                        .eq(WebhookSubscriptionEntity::getDeleted, 0)
                        .orderByDesc(WebhookSubscriptionEntity::getId))
                .stream()
                .map(this::toWebhook)
                .toList();
    }

    @Override
    @Transactional
    public AdminWebhookResponse saveWebhook(AdminWebhookUpsertRequest request) {
        WebhookSubscriptionEntity entity;
        if (request.getId() != null) {
            entity = webhookMapper.selectById(request.getId());
            if (entity == null || entity.getDeleted() != null && entity.getDeleted() != 0) {
                throw new BusinessException("WEBHOOK_NOT_FOUND", "Webhook 不存在");
            }
        } else {
            entity = new WebhookSubscriptionEntity();
            entity.setDeleted(0);
            entity.setFailureCount(0);
        }
        entity.setName(request.getName());
        entity.setCallbackUrl(request.getCallbackUrl());
        entity.setSecretToken(fieldEncryptionService.encrypt(request.getSecretToken()));
        entity.setEventTypes(request.getEventTypes());
        entity.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        if (request.getId() == null) {
            webhookMapper.insert(entity);
        } else {
            webhookMapper.updateById(entity);
        }
        return toWebhook(entity);
    }

    @Override
    public void deleteWebhook(Long id) {
        WebhookSubscriptionEntity entity = webhookMapper.selectById(id);
        if (entity != null) {
            entity.setDeleted(1);
            webhookMapper.updateById(entity);
        }
    }

    @Override
    public List<AdminExternalApiKeyResponse> listApiKeys() {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ExternalApiKeyEntity>()
                        .eq(ExternalApiKeyEntity::getDeleted, 0)
                        .orderByDesc(ExternalApiKeyEntity::getId))
                .stream()
                .map(this::toApiKey)
                .toList();
    }

    @Override
    @Transactional
    public AdminExternalApiKeyResponse createApiKey(String keyName, Integer rateLimitPerMinute) {
        ExternalApiKeyEntity entity = new ExternalApiKeyEntity();
        entity.setKeyName(keyName);
        entity.setApiKey("fsd_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setStatus("ACTIVE");
        entity.setRateLimitPerMinute(Objects.requireNonNullElse(rateLimitPerMinute, 120));
        entity.setTotalCalls(0L);
        entity.setDeleted(0);
        apiKeyMapper.insert(entity);
        return toApiKey(entity);
    }

    @Override
    public List<AdminWebhookDeliveryLogResponse> listDeliveryLogs(Long subscriptionId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        // SEC-17 fix: use Page<> instead of .last("LIMIT " + n) to avoid SQL string
        // concatenation, even when the value is server-side bounded.
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<WebhookDeliveryLogEntity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, capped);
        return deliveryLogMapper.selectPage(page, new LambdaQueryWrapper<WebhookDeliveryLogEntity>()
                        .eq(WebhookDeliveryLogEntity::getSubscriptionId, subscriptionId)
                        .orderByDesc(WebhookDeliveryLogEntity::getDeliveredAt))
                .getRecords()
                .stream()
                .map(this::toDeliveryLog)
                .toList();
    }

    @Override
    public void disableApiKey(Long id) {
        ExternalApiKeyEntity entity = apiKeyMapper.selectById(id);
        if (entity != null) {
            entity.setStatus("DISABLED");
            apiKeyMapper.updateById(entity);
        }
    }

    private AdminWebhookResponse toWebhook(WebhookSubscriptionEntity entity) {
        return AdminWebhookResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .callbackUrl(entity.getCallbackUrl())
                .eventTypes(entity.getEventTypes())
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .failureCount(entity.getFailureCount())
                .lastDeliveryAt(entity.getLastDeliveryAt())
                .build();
    }

    private AdminExternalApiKeyResponse toApiKey(ExternalApiKeyEntity entity) {
        return AdminExternalApiKeyResponse.builder()
                .id(entity.getId())
                .keyName(entity.getKeyName())
                .apiKey(maskApiKey(entity.getApiKey()))
                .status(entity.getStatus())
                .rateLimitPerMinute(entity.getRateLimitPerMinute())
                .totalCalls(entity.getTotalCalls())
                .rateLimitHits(entity.getRateLimitHits())
                .lastUsedAt(entity.getLastUsedAt())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private AdminWebhookDeliveryLogResponse toDeliveryLog(WebhookDeliveryLogEntity entity) {
        return AdminWebhookDeliveryLogResponse.builder()
                .id(entity.getId())
                .subscriptionId(entity.getSubscriptionId())
                .eventType(entity.getEventType())
                .businessKey(entity.getBusinessKey())
                .httpStatus(entity.getHttpStatus())
                .success(entity.getSuccess() != null && entity.getSuccess() == 1)
                .attemptNo(entity.getAttemptNo())
                .payloadSummary(entity.getPayloadSummary())
                .errorMessage(entity.getErrorMessage())
                .deliveredAt(entity.getDeliveredAt())
                .build();
    }
}
