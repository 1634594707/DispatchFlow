package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminApiKeyCreateRequest;
import com.fsd.admin.dto.AdminWebhookUpsertRequest;
import com.fsd.admin.service.IntegrationAdminService;
import com.fsd.admin.vo.AdminExternalApiKeyResponse;
import com.fsd.admin.vo.AdminWebhookDeliveryLogResponse;
import com.fsd.admin.vo.AdminWebhookResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@Tag(name = "Integration", description = "Webhooks, API keys, and delivery logs")
@SecurityRequirement(name = "adminToken")
public class AdminIntegrationController {

    private final IntegrationAdminService integrationAdminService;

    public AdminIntegrationController(IntegrationAdminService integrationAdminService) {
        this.integrationAdminService = integrationAdminService;
    }

    @GetMapping("/webhooks")
    @Operation(summary = "List webhooks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Webhook list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminWebhookResponse>> listWebhooks(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.listWebhooks());
    }

    @PostMapping("/webhooks")
    @Operation(summary = "Create or update webhook")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Webhook saved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminWebhookResponse> saveWebhook(@Valid @RequestBody AdminWebhookUpsertRequest body,
                                                         HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.saveWebhook(body));
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @Operation(summary = "List webhook delivery logs")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Delivery logs returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminWebhookDeliveryLogResponse>> listDeliveries(@PathVariable Long id,
                                                                             @RequestParam(defaultValue = "50") int limit,
                                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.listDeliveryLogs(id, limit));
    }

    @DeleteMapping("/webhooks/{id}")
    @Operation(summary = "Delete webhook")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Webhook deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> deleteWebhook(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        integrationAdminService.deleteWebhook(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api-keys")
    @Operation(summary = "List external API keys")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "API key list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<List<AdminExternalApiKeyResponse>> listApiKeys(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.listApiKeys());
    }

    @PostMapping("/api-keys")
    @Operation(summary = "Create external API key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "API key created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminExternalApiKeyResponse> createApiKey(@RequestBody AdminApiKeyCreateRequest body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(integrationAdminService.createApiKey(body.getKeyName(), body.getRateLimitPerMinute()));
    }

    @PostMapping("/api-keys/{id}/disable")
    @Operation(summary = "Disable an API key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "API key disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<Void> disableApiKey(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        integrationAdminService.disableApiKey(id);
        return ApiResponse.success(null);
    }
}
