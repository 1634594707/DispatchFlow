package com.fsd.dispatch.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final WebhookSubscriptionMapper subscriptionMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public WebhookDeliveryService(WebhookSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
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
            try {
                String body = "{\"eventType\":\"" + event.getEventType() + "\",\"businessKey\":\""
                        + event.getBusinessKey() + "\",\"eventTime\":\"" + event.getEventTime() + "\"}";
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(sub.getCallbackUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                if (sub.getSecretToken() != null && !sub.getSecretToken().isBlank()) {
                    builder.header("X-Webhook-Secret", sub.getSecretToken());
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    sub.setFailureCount(0);
                    sub.setLastDeliveryAt(LocalDateTime.now());
                    subscriptionMapper.updateById(sub);
                } else {
                    markFailure(sub);
                }
            } catch (Exception ex) {
                log.warn("Webhook delivery failed for {}: {}", sub.getCallbackUrl(), ex.getMessage());
                markFailure(sub);
            }
        }
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
}
