package com.fsd.dispatch.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.security.FieldEncryptionService;
import com.fsd.dispatch.entity.WebhookDeliveryLogEntity;
import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.mapper.WebhookDeliveryLogMapper;
import com.fsd.dispatch.mapper.WebhookSubscriptionMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final WebhookSubscriptionMapper subscriptionMapper;
    private final WebhookDeliveryLogMapper deliveryLogMapper;
    private final FieldEncryptionService fieldEncryptionService;
    private final RobotMessageFormatter messageFormatter;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public WebhookDeliveryService(WebhookSubscriptionMapper subscriptionMapper,
                                  WebhookDeliveryLogMapper deliveryLogMapper,
                                  FieldEncryptionService fieldEncryptionService,
                                  RobotMessageFormatter messageFormatter) {
        this.subscriptionMapper = subscriptionMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.fieldEncryptionService = fieldEncryptionService;
        this.messageFormatter = messageFormatter;
    }

    public void deliver(DispatchDomainEvent event) {
        List<WebhookSubscriptionEntity> subs = subscriptionMapper.selectList(
                new LambdaQueryWrapper<WebhookSubscriptionEntity>()
                        .eq(WebhookSubscriptionEntity::getDeleted, 0)
                        .eq(WebhookSubscriptionEntity::getEnabled, 1));
        for (WebhookSubscriptionEntity sub : subs) {
            if (!matches(sub.getEventTypes(), event.getEventType())) {
                continue;
            }
            String channelType = sub.getChannelType() != null ? sub.getChannelType() : "GENERIC";
            String body = messageFormatter.format(channelType, event);
            String summary = truncate(body, 480);
            int attempt = (sub.getFailureCount() == null ? 0 : sub.getFailureCount()) + 1;
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(sub.getCallbackUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                if (sub.getSecretToken() != null && !sub.getSecretToken().isBlank()) {
                    String secret = fieldEncryptionService.decrypt(sub.getSecretToken());
                    if (secret != null && !secret.isBlank()) {
                        builder.header("X-Webhook-Secret", secret);
                    }
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                persistLog(sub.getId(), event, summary, response.statusCode(), success, attempt, null);
                if (success) {
                    sub.setFailureCount(0);
                    sub.setLastDeliveryAt(LocalDateTime.now());
                    subscriptionMapper.updateById(sub);
                } else {
                    markFailure(sub);
                }
            } catch (Exception ex) {
                log.warn("Webhook delivery failed for {}: {}", sub.getCallbackUrl(), ex.getMessage());
                persistLog(sub.getId(), event, summary, null, false, attempt, truncate(ex.getMessage(), 480));
                markFailure(sub);
            }
        }
    }

    private void persistLog(Long subscriptionId, DispatchDomainEvent event, String summary,
                            Integer httpStatus, boolean success, int attempt, String error) {
        WebhookDeliveryLogEntity logEntity = new WebhookDeliveryLogEntity();
        logEntity.setSubscriptionId(subscriptionId);
        logEntity.setEventType(event.getEventType());
        logEntity.setBusinessKey(event.getBusinessKey());
        logEntity.setHttpStatus(httpStatus);
        logEntity.setSuccess(success ? 1 : 0);
        logEntity.setAttemptNo(attempt);
        logEntity.setPayloadSummary(summary);
        logEntity.setErrorMessage(error);
        logEntity.setDeliveredAt(LocalDateTime.now());
        deliveryLogMapper.insert(logEntity);
    }

    private boolean matches(String eventTypes, String eventType) {
        if (eventTypes == null || eventTypes.isBlank()) {
            return true;
        }
        return Arrays.stream(eventTypes.split(","))
                .map(String::trim)
                .anyMatch(type -> type.equals(eventType) || "dispatch.#".equals(type));
    }

    private void markFailure(WebhookSubscriptionEntity sub) {
        sub.setFailureCount((sub.getFailureCount() == null ? 0 : sub.getFailureCount()) + 1);
        subscriptionMapper.updateById(sub);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
