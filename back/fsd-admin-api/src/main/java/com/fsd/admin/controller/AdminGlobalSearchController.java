package com.fsd.admin.controller;

import com.fsd.admin.service.GlobalSearchAdminService;
import com.fsd.admin.vo.AdminGlobalSearchResponse;
import com.fsd.common.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/search")
public class AdminGlobalSearchController {

    private final GlobalSearchAdminService globalSearchAdminService;

    public AdminGlobalSearchController(GlobalSearchAdminService globalSearchAdminService) {
        this.globalSearchAdminService = globalSearchAdminService;
    }

    @GetMapping
    public ApiResponse<AdminGlobalSearchResponse> search(@RequestParam String keyword,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(globalSearchAdminService.search(keyword, limit));
    }
}
