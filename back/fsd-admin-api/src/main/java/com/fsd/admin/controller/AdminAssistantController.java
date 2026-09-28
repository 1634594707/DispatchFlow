package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.DispatchAssistantService;
import com.fsd.admin.vo.AdminAssistantBriefingResponse;
import com.fsd.admin.vo.AdminAssistantDecisionExplanationResponse;
import com.fsd.admin.vo.AdminAssistantDigestResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P2-1：只读调度助手端点。
 *
 * <p>只有 GET——派车/重派/阈值修改是确定性操作，助手层永远不提供（面试路线图 P2-1 第 4 条）。
 * 数据口径与运营分析同源（见 {@link DispatchAssistantService}）。
 */
@RestController
@RequestMapping("/admin/assistant")
@Tag(name = "Assistant", description = "Read-only dispatch assistant (briefing / decision explanation / digest)")
@SecurityRequirement(name = "Authorization")
public class AdminAssistantController {

    private final DispatchAssistantService dispatchAssistantService;

    public AdminAssistantController(DispatchAssistantService dispatchAssistantService) {
        this.dispatchAssistantService = dispatchAssistantService;
    }

    @GetMapping("/briefing")
    @Operation(summary = "Operations briefing", description = "Fleet metrics, forecast availability, snapshot stats and exception overview")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Briefing returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAssistantBriefingResponse> briefing(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAssistantService.getBriefing(period, parkId));
    }

    @GetMapping("/decisions/{orderId}")
    @Operation(summary = "Decision explanation", description = "Deterministic explanation of why an order was dispatched the way it was")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Explanation returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAssistantDecisionExplanationResponse> explain(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAssistantService.explainDecision(orderId));
    }

    @GetMapping("/digest")
    @Operation(summary = "Daily digest", description = "Exception summary and daily operations digest")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Digest returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAssistantDigestResponse> digest(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(dispatchAssistantService.getDigest(date, parkId));
    }
}
