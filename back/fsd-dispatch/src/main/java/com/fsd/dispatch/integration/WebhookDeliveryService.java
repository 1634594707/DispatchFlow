package com.fsd.dispatch.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.security.FieldEncryptionService;
import com.fsd.dispatch.entity.WebhookDeliveryLogEntity;
import com.fsd.dispatch.entity.WebhookSubscriptionEntity;
import com.fsd.dispatch.event.DispatchDomainEvent;
import com.fsd.dispatch.mapper.WebhookDeliveryLogMapper;
import com.fsd.dispatch.mapper.WebhookSubscriptionMapper;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int CIRCUIT_BREAKER_FAILURES = 5;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebhookSubscriptionMapper subscriptionMapper;
    private final WebhookDeliveryLogMapper deliveryLogMapper;
    private final FieldEncryptionService fieldEncryptionService;
    private final RobotMessageFormatter messageFormatter;
    private final ExecutorService deliveryExecutor = Executors.newFixedThreadPool(4);
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

    @PreDestroy
    public void shutdown() {
        deliveryExecutor.shutdown();
        try {
            if (!deliveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            deliveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
            if (isCircuitOpen(sub)) {
                persistLog(sub.getId(), event, "Webhook delivery skipped by circuit breaker", null,
                        false, sub.getFailureCount(), "WEBHOOK_CIRCUIT_OPEN");
                continue;
            }
            deliveryExecutor.execute(() -> deliverToSubscription(sub, event));
        }
    }

    private void deliverToSubscription(WebhookSubscriptionEntity sub, DispatchDomainEvent event) {
        String channelType = sub.getChannelType() != null ? sub.getChannelType() : "GENERIC";
        String body = messageFormatter.format(channelType, event);
        String summary = truncate(body, 480);
        int baseAttempt = sub.getFailureCount() == null ? 0 : sub.getFailureCount();

        // SEC-15: SSRF guard — reject callback URLs that resolve to private/loopback/link-local
        // addresses before issuing any outbound request.
        try {
            assertCallbackUrlSafe(sub.getCallbackUrl());
        } catch (Exception ex) {
            log.warn("Webhook callback URL rejected (SSRF guard): {} -> {}", sub.getCallbackUrl(), ex.getMessage());
            persistLog(sub.getId(), event, summary, null, false, baseAttempt + 1,
                    "WEBHOOK_SSRF_BLOCKED:" + truncate(ex.getMessage(), 200));
            markFailure(sub);
            return;
        }

        String secret = null;
        if (sub.getSecretToken() != null && !sub.getSecretToken().isBlank()) {
            secret = fieldEncryptionService.decrypt(sub.getSecretToken());
        }

        for (int retryIndex = 0; retryIndex < MAX_RETRY_ATTEMPTS; retryIndex++) {
            int attempt = baseAttempt + retryIndex + 1;
            try {
                if (retryIndex > 0) {
                    Thread.sleep(backoffMs(retryIndex));
                }
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(sub.getCallbackUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                // SEC-16: send HMAC-SHA256 signature of the payload so receivers can
                // verify integrity. The X-Webhook-Secret header is retained for backwards
                // compatibility but new integrations should rely on the signature.
                if (secret != null && !secret.isBlank()) {
                    builder.header("X-Webhook-Secret", secret);
                    builder.header("X-Webhook-Signature", hmacSha256Hex(secret, body));
                    builder.header("X-Webhook-Signature-Alg", "HMAC-SHA256");
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                persistLog(sub.getId(), event, summary, response.statusCode(), success, attempt, null);
                if (success) {
                    sub.setFailureCount(0);
                    sub.setLastDeliveryAt(LocalDateTime.now());
                    subscriptionMapper.updateById(sub);
                    return;
                }
                if (retryIndex == MAX_RETRY_ATTEMPTS - 1) {
                    markFailure(sub);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                persistLog(sub.getId(), event, summary, null, false, attempt, "WEBHOOK_RETRY_INTERRUPTED");
                markFailure(sub);
                return;
            } catch (Exception ex) {
                log.warn("Webhook delivery failed for {}: {}", sub.getCallbackUrl(), ex.getMessage());
                persistLog(sub.getId(), event, summary, null, false, attempt, truncate(ex.getMessage(), 480));
                if (retryIndex == MAX_RETRY_ATTEMPTS - 1) {
                    markFailure(sub);
                }
            }
        }
    }

    /**
     * SEC-15: Resolve the host of the callback URL and reject any address that points to
     * private, loopback, link-local, or cloud-metadata endpoints. This prevents SSRF
     * attacks that target internal services (e.g. http://169.254.169.254/).
     */
    private void assertCallbackUrlSafe(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("callback URL is empty");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("malformed callback URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("only http/https schemes are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("callback URL missing host");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("cannot resolve host: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                throw new IllegalArgumentException("host resolves to a non-public address: " + addr.getHostAddress());
            }
            // Block well-known cloud metadata endpoints (AWS/GCP/Azure) by prefix.
            String ip = addr.getHostAddress();
            if (ip.startsWith("169.254.") || ip.startsWith("100.64.") || ip.startsWith("fd00:")
                    || ip.startsWith("fe80:") || ip.equals("0.0.0.0")) {
                throw new IllegalArgumentException("host resolves to a blocked metadata/private range: " + ip);
            }
        }
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            // Should never happen for HmacSHA256; fall back to a digest-free marker so the
            // receiver can still reject if it expects a signature.
            log.warn("HMAC-SHA256 signing failed: {}", ex.getMessage());
            return "";
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

    private boolean isCircuitOpen(WebhookSubscriptionEntity sub) {
        return sub.getFailureCount() != null && sub.getFailureCount() >= CIRCUIT_BREAKER_FAILURES;
    }

    private long backoffMs(int retryIndex) {
        return 500L * (1L << Math.max(0, retryIndex - 1));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
