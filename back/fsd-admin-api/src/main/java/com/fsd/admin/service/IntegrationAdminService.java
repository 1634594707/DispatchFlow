package com.fsd.admin.service;

import com.fsd.admin.dto.AdminWebhookUpsertRequest;
import com.fsd.admin.vo.AdminExternalApiKeyResponse;
import com.fsd.admin.vo.AdminWebhookDeliveryLogResponse;
import com.fsd.admin.vo.AdminWebhookResponse;
import java.util.List;

public interface IntegrationAdminService {

    List<AdminWebhookResponse> listWebhooks();

    AdminWebhookResponse saveWebhook(AdminWebhookUpsertRequest request);

    void deleteWebhook(Long id);

    List<AdminExternalApiKeyResponse> listApiKeys();

    AdminExternalApiKeyResponse createApiKey(String keyName, Integer rateLimitPerMinute);

    void disableApiKey(Long id);

    List<AdminWebhookDeliveryLogResponse> listDeliveryLogs(Long subscriptionId, int limit);
}
