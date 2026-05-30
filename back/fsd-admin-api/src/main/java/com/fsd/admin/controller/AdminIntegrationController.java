package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminWebhookUpsertRequest;
import com.fsd.admin.service.IntegrationAdminService;
import com.fsd.admin.vo.AdminExternalApiKeyResponse;
import com.fsd.admin.vo.AdminWebhookResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integration")
public class AdminIntegrationController {

    private final IntegrationAdminService integrationAdminService;

    public AdminIntegrationController(IntegrationAdminService integrationAdminService) {
        this.integrationAdminService = integrationAdminService;
    }

    @GetMapping("/webhooks")
    public ApiResponse<List<AdminWebhookResponse>> listWebhooks(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.listWebhooks());
    }

    @PostMapping("/webhooks")
    public ApiResponse<AdminWebhookResponse> saveWebhook(@Valid @RequestBody AdminWebhookUpsertRequest body,
                                                         HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.saveWebhook(body));
    }

    @DeleteMapping("/webhooks/{id}")
    public ApiResponse<Void> deleteWebhook(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        integrationAdminService.deleteWebhook(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api-keys")
    public ApiResponse<List<AdminExternalApiKeyResponse>> listApiKeys(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.listApiKeys());
    }

    @PostMapping("/api-keys")
    public ApiResponse<AdminExternalApiKeyResponse> createApiKey(@RequestBody Map<String, Object> body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        String keyName = body.get("keyName") == null ? "external" : body.get("keyName").toString();
        Integer limit = body.get("rateLimitPerMinute") == null ? 120
                : Integer.parseInt(body.get("rateLimitPerMinute").toString());
        return ApiResponse.success(integrationAdminService.createApiKey(keyName, limit));
    }

    @PostMapping("/api-keys/{id}/disable")
    public ApiResponse<Void> disableApiKey(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        integrationAdminService.disableApiKey(id);
        return ApiResponse.success(null);
    }
}
