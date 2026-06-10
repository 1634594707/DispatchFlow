package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.RoadRouteHealthAdminService;
import com.fsd.admin.service.RoadRouteValidateAdminService;
import com.fsd.admin.vo.RoadRouteHealthResponse;
import com.fsd.admin.vo.RoadRouteValidateRequest;
import com.fsd.admin.vo.RoadRouteValidateResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/park/road-route")
@Tag(name = "Road Route Health", description = "道路路径健康检查")
@SecurityRequirement(name = "adminToken")
public class RoadRouteHealthController {

    private final RoadRouteHealthAdminService roadRouteHealthAdminService;
    private final RoadRouteValidateAdminService roadRouteValidateAdminService;

    public RoadRouteHealthController(RoadRouteHealthAdminService roadRouteHealthAdminService,
                                     RoadRouteValidateAdminService roadRouteValidateAdminService) {
        this.roadRouteHealthAdminService = roadRouteHealthAdminService;
        this.roadRouteValidateAdminService = roadRouteValidateAdminService;
    }

    @GetMapping("/health")
    @Operation(summary = "道路路径健康检查", description = "返回高德驾车路径、本地路网图及降级计数")
    public ApiResponse<RoadRouteHealthResponse> health(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(roadRouteHealthAdminService.getHealth());
    }

    @PostMapping("/validate")
    @Operation(summary = "路线合法性校验", description = "检测 polyline 是否穿越建筑/水域（M8-R8）")
    public ApiResponse<RoadRouteValidateResponse> validate(HttpServletRequest request,
                                                           @RequestBody RoadRouteValidateRequest body) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(roadRouteValidateAdminService.validate(body));
    }
}