package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminAssistantRequest;
import com.fsd.admin.service.DispatchAssistantAdminService;
import com.fsd.admin.vo.AdminAssistantResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assistant")
@Tag(name = "Dispatch Assistant", description = "Rule-based voice/text command interpretation")
@SecurityRequirement(name = "adminToken")
public class AdminDispatchAssistantController {

    private final DispatchAssistantAdminService dispatchAssistantAdminService;

    public AdminDispatchAssistantController(DispatchAssistantAdminService dispatchAssistantAdminService) {
        this.dispatchAssistantAdminService = dispatchAssistantAdminService;
    }

    @PostMapping("/interpret")
    @Operation(summary = "Interpret dispatch command", description = "Parse a natural-language or shorthand command into structured dispatch actions")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Interpretation result returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unrecognized command"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAssistantResponse> interpret(@Valid @RequestBody AdminAssistantRequest request,
                                                        HttpServletRequest httpRequest) {
        AdminAuthSupport.requireAuth(httpRequest);
        return ApiResponse.success(dispatchAssistantAdminService.interpret(request));
    }
}
