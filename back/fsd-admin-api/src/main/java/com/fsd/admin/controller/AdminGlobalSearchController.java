package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.GlobalSearchAdminService;
import com.fsd.admin.vo.AdminGlobalSearchResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/search")
@Tag(name = "Global Search", description = "Cross-entity keyword search for admin console")
@SecurityRequirement(name = "adminToken")
public class AdminGlobalSearchController {

    private final GlobalSearchAdminService globalSearchAdminService;

    public AdminGlobalSearchController(GlobalSearchAdminService globalSearchAdminService) {
        this.globalSearchAdminService = globalSearchAdminService;
    }

    @GetMapping
    @Operation(summary = "Global keyword search", description = "Search across orders, tasks, vehicles, and exceptions")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminGlobalSearchResponse> search(@RequestParam String keyword,
                                                        @RequestParam(defaultValue = "20") int limit,
                                                        HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(globalSearchAdminService.search(keyword, limit));
    }
}
