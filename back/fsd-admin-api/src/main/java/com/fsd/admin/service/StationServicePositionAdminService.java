package com.fsd.admin.service;

import com.fsd.admin.dto.AdminMapDataVersionUpsertRequest;
import com.fsd.admin.dto.AdminStationServicePositionUpsertRequest;
import com.fsd.admin.vo.AdminMapDataVersionResponse;
import com.fsd.admin.vo.AdminStationServicePositionResponse;
import java.util.List;

/**
 * 站点服务位 + 地图数据版本 管理服务（P0-5 / P1-7 / P1-10 / P2-6）。
 */
public interface StationServicePositionAdminService {

    // ===== 站点服务位 =====

    List<AdminStationServicePositionResponse> listByStation(Long stationId);

    AdminStationServicePositionResponse create(AdminStationServicePositionUpsertRequest request);

    AdminStationServicePositionResponse update(Long positionId, AdminStationServicePositionUpsertRequest request);

    AdminStationServicePositionResponse toggleStatus(Long positionId);

    void delete(Long positionId);

    /** 查询某站点当前可用的服务位 */
    List<AdminStationServicePositionResponse> listAvailable(Long stationId);

    // ===== 地图数据版本 =====

    List<AdminMapDataVersionResponse> listMapVersions(Long parkId);

    AdminMapDataVersionResponse getActiveMapVersion(Long parkId);

    AdminMapDataVersionResponse createMapVersion(AdminMapDataVersionUpsertRequest request);

    AdminMapDataVersionResponse activateMapVersion(Long versionId);
}
