package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminDispatchStrategyUpsertRequest;
import com.fsd.admin.service.DispatchStrategyAdminService;
import com.fsd.admin.vo.AdminDispatchStrategyResponse;
import com.fsd.admin.vo.AdminStrategyChangeLogResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dispatch/strategy")
@Tag(name = "Dispatch Strategy", description = "Strategy profiles, activation, and change logs")
public class AdminDispatchStrategyController {

    private final DispatchStrategyAdminService strategyAdminService;

    public AdminDispatchStrategyController(DispatchStrategyAdminService strategyAdminService) {
        this.strategyAdminService = strategyAdminService;
    }

    @GetMapping("/profiles")
    public ApiResponse<List<AdminDispatchStrategyResponse>> listProfiles(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(strategyAdminService.listProfiles());
    }

    @GetMapping("/change-logs")
    public ApiResponse<List<AdminStrategyChangeLogResponse>> changeLogs(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(strategyAdminService.listChangeLogs());
    }

    @PostMapping("/profiles")
    public ApiResponse<AdminDispatchStrategyResponse> create(@Valid @RequestBody AdminDispatchStrategyUpsertRequest body,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(strategyAdminService.create(body, operatorName(request)));
    }

    @PutMapping("/profiles/{id}")
    public ApiResponse<AdminDispatchStrategyResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody AdminDispatchStrategyUpsertRequest body,
                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(strategyAdminService.update(id, body, operatorName(request)));
    }

    @PostMapping("/profiles/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        strategyAdminService.activate(id, operatorName(request));
        return ApiResponse.success(null);
    }

    private String operatorName(HttpServletRequest request) {
        var ctx = AdminAuthSupport.fromRequest(request);
        if (ctx == null || ctx.getDisplayName() == null) {
            return "admin";
        }
        return ctx.getDisplayName();
    }
}
