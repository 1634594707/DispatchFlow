package com.fsd.admin.controller;

import com.fsd.admin.dto.AdminAssistantRequest;
import com.fsd.admin.service.DispatchAssistantAdminService;
import com.fsd.admin.vo.AdminAssistantResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assistant")
public class AdminDispatchAssistantController {

    private final DispatchAssistantAdminService dispatchAssistantAdminService;

    public AdminDispatchAssistantController(DispatchAssistantAdminService dispatchAssistantAdminService) {
        this.dispatchAssistantAdminService = dispatchAssistantAdminService;
    }

    @PostMapping("/interpret")
    public ApiResponse<AdminAssistantResponse> interpret(@Valid @RequestBody AdminAssistantRequest request) {
        return ApiResponse.success(dispatchAssistantAdminService.interpret(request));
    }
}
