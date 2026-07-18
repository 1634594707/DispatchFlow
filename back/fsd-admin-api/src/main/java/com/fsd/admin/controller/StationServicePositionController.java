package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminMapDataVersionUpsertRequest;
import com.fsd.admin.dto.AdminStationServicePositionUpsertRequest;
import com.fsd.admin.service.StationServicePositionAdminService;
import com.fsd.admin.vo.AdminMapDataVersionResponse;
import com.fsd.admin.vo.AdminStationServicePositionResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点服务位 + 地图数据版本 管理接口（P0-5 / P1-7 / P1-10 / P2-6）。
 */
@RestController
@RequestMapping("/api/admin/infrastructure")
@Tag(name = "Service Position & Map Version", description = "站点服务位 CRUD、预约查询、地图数据版本管理")
@SecurityRequirement(name = "adminToken")
public class StationServicePositionController {

    private final StationServicePositionAdminService service;

    public StationServicePositionController(StationServicePositionAdminService service) {
        this.service = service;
    }

    // ===== 站点服务位 =====

    @GetMapping("/stations/{stationId}/service-positions")
    @Operation(summary = "列出某站点的服务位", description = "P0-5/P1-7：返回某站点配置的所有服务位")
    public ApiResponse<List<AdminStationServicePositionResponse>> listByStation(
            @PathVariable Long stationId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.listByStation(stationId));
    }

    @GetMapping("/stations/{stationId}/service-positions/available")
    @Operation(summary = "查询某站点当前可用服务位", description = "P1-10：返回 status=ACTIVE 且未被预约的服务位")
    public ApiResponse<List<AdminStationServicePositionResponse>> listAvailable(
            @PathVariable Long stationId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.listAvailable(stationId));
    }

    @PostMapping("/service-positions")
    @Operation(summary = "新增服务位")
    public ApiResponse<AdminStationServicePositionResponse> create(
            @Valid @RequestBody AdminStationServicePositionUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.create(body));
    }

    @PutMapping("/service-positions/{positionId}")
    @Operation(summary = "更新服务位")
    public ApiResponse<AdminStationServicePositionResponse> update(
            @PathVariable Long positionId,
            @Valid @RequestBody AdminStationServicePositionUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.update(positionId, body));
    }

    @PostMapping("/service-positions/{positionId}/toggle-status")
    @Operation(summary = "切换服务位状态", description = "ACTIVE ↔ OUT_OF_SERVICE/MAINTENANCE/RESERVED/OCCUPIED")
    public ApiResponse<AdminStationServicePositionResponse> toggleStatus(
            @PathVariable Long positionId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.toggleStatus(positionId));
    }

    @PostMapping("/service-positions/{positionId}/delete")
    @Operation(summary = "删除服务位")
    public ApiResponse<Void> delete(@PathVariable Long positionId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        service.delete(positionId);
        return ApiResponse.success(null);
    }

    // ===== 地图数据版本 =====

    @GetMapping("/map-versions")
    @Operation(summary = "列出地图数据版本", description = "P2-6：可按园区过滤")
    public ApiResponse<List<AdminMapDataVersionResponse>> listMapVersions(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.listMapVersions(parkId));
    }

    @GetMapping("/map-versions/active")
    @Operation(summary = "查询某园区当前激活的地图数据版本")
    public ApiResponse<AdminMapDataVersionResponse> getActiveMapVersion(
            @RequestParam Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.getActiveMapVersion(parkId));
    }

    @PostMapping("/map-versions")
    @Operation(summary = "新增地图数据版本")
    public ApiResponse<AdminMapDataVersionResponse> createMapVersion(
            @Valid @RequestBody AdminMapDataVersionUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.createMapVersion(body));
    }

    @PostMapping("/map-versions/{versionId}/activate")
    @Operation(summary = "激活某地图数据版本", description = "自动取消该园区其他版本的激活状态")
    public ApiResponse<AdminMapDataVersionResponse> activateMapVersion(
            @PathVariable Long versionId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(service.activateMapVersion(versionId));
    }
}
