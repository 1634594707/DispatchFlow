package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fsd.admin.dto.AdminMapDataVersionUpsertRequest;
import com.fsd.admin.dto.AdminStationServicePositionUpsertRequest;
import com.fsd.admin.service.StationServicePositionAdminService;
import com.fsd.admin.vo.AdminMapDataVersionResponse;
import com.fsd.admin.vo.AdminStationServicePositionResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.MapDataVersionEntity;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.entity.StationServicePositionEntity;
import com.fsd.dispatch.mapper.MapDataVersionMapper;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.mapper.StationServicePositionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationServicePositionAdminServiceImpl implements StationServicePositionAdminService {

    private final StationServicePositionMapper positionMapper;
    private final StationMapper stationMapper;
    private final ParkMapper parkMapper;
    private final MapDataVersionMapper mapDataVersionMapper;

    public StationServicePositionAdminServiceImpl(StationServicePositionMapper positionMapper,
                                                    StationMapper stationMapper,
                                                    ParkMapper parkMapper,
                                                    MapDataVersionMapper mapDataVersionMapper) {
        this.positionMapper = positionMapper;
        this.stationMapper = stationMapper;
        this.parkMapper = parkMapper;
        this.mapDataVersionMapper = mapDataVersionMapper;
    }

    // ===== 站点服务位 =====

    @Override
    public List<AdminStationServicePositionResponse> listByStation(Long stationId) {
        if (stationId == null) {
            return List.of();
        }
        StationEntity station = requireStation(stationId);
        Map<Long, StationEntity> stationMap = Map.of(station.getId(), station);
        return positionMapper.selectList(new LambdaQueryWrapper<StationServicePositionEntity>()
                        .eq(StationServicePositionEntity::getStationId, stationId)
                        .eq(StationServicePositionEntity::getDeleted, 0)
                        .orderByAsc(StationServicePositionEntity::getPositionCode))
                .stream()
                .map(p -> toResponse(p, stationMap.get(p.getStationId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminStationServicePositionResponse create(AdminStationServicePositionUpsertRequest request) {
        if (request.getStationId() == null) {
            throw new BusinessException("STATION_REQUIRED", "站点ID不能为空");
        }
        StationEntity station = requireStation(request.getStationId());
        if (request.getPositionCode() == null || request.getPositionCode().isBlank()) {
            throw new BusinessException("POSITION_CODE_REQUIRED", "服务位编码不能为空");
        }
        StationServicePositionEntity entity = new StationServicePositionEntity();
        applyFields(entity, request);
        entity.setStationId(station.getId());
        entity.setStatus(resolveStatus(request.getStatus(), "ACTIVE"));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        positionMapper.insert(entity);
        return toResponse(entity, station);
    }

    @Override
    @Transactional
    public AdminStationServicePositionResponse update(Long positionId, AdminStationServicePositionUpsertRequest request) {
        StationServicePositionEntity entity = requirePosition(positionId);
        StationEntity station = requireStation(entity.getStationId());
        applyFields(entity, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            entity.setStatus(resolveStatus(request.getStatus(), "ACTIVE"));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        positionMapper.updateById(entity);
        return toResponse(entity, station);
    }

    @Override
    @Transactional
    public AdminStationServicePositionResponse toggleStatus(Long positionId) {
        StationServicePositionEntity entity = requirePosition(positionId);
        StationEntity station = requireStation(entity.getStationId());
        String status = entity.getStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            entity.setStatus("OUT_OF_SERVICE");
        } else if ("OUT_OF_SERVICE".equalsIgnoreCase(status)
                || "MAINTENANCE".equalsIgnoreCase(status)
                || "RESERVED".equalsIgnoreCase(status)
                || "OCCUPIED".equalsIgnoreCase(status)) {
            entity.setStatus("ACTIVE");
            entity.setReservedVehicleId(null);
            entity.setReservedUntil(null);
        } else {
            entity.setStatus("ACTIVE");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        positionMapper.updateById(entity);
        return toResponse(entity, station);
    }

    @Override
    @Transactional
    public void delete(Long positionId) {
        StationServicePositionEntity entity = requirePosition(positionId);
        entity.setDeleted(1);
        entity.setUpdatedAt(LocalDateTime.now());
        positionMapper.updateById(entity);
    }

    @Override
    public List<AdminStationServicePositionResponse> listAvailable(Long stationId) {
        if (stationId == null) {
            return List.of();
        }
        StationEntity station = requireStation(stationId);
        Map<Long, StationEntity> stationMap = Map.of(station.getId(), station);
        return positionMapper.selectList(new LambdaQueryWrapper<StationServicePositionEntity>()
                        .eq(StationServicePositionEntity::getStationId, stationId)
                        .eq(StationServicePositionEntity::getStatus, "ACTIVE")
                        .isNull(StationServicePositionEntity::getReservedVehicleId)
                        .eq(StationServicePositionEntity::getDeleted, 0)
                        .orderByAsc(StationServicePositionEntity::getPositionCode))
                .stream()
                .map(p -> toResponse(p, stationMap.get(p.getStationId())))
                .toList();
    }

    // ===== 地图数据版本 =====

    @Override
    public List<AdminMapDataVersionResponse> listMapVersions(Long parkId) {
        LambdaQueryWrapper<MapDataVersionEntity> wrapper = new LambdaQueryWrapper<MapDataVersionEntity>()
                .eq(MapDataVersionEntity::getDeleted, 0)
                .orderByDesc(MapDataVersionEntity::getIsActive)
                .orderByDesc(MapDataVersionEntity::getPublishedAt);
        if (parkId != null) {
            wrapper.eq(MapDataVersionEntity::getParkId, parkId);
        }
        Map<Long, ParkEntity> parkMap = loadParkMap();
        return mapDataVersionMapper.selectList(wrapper).stream()
                .map(v -> toVersionResponse(v, parkMap.get(v.getParkId())))
                .toList();
    }

    @Override
    public AdminMapDataVersionResponse getActiveMapVersion(Long parkId) {
        if (parkId == null) {
            return null;
        }
        Map<Long, ParkEntity> parkMap = loadParkMap();
        MapDataVersionEntity entity = mapDataVersionMapper.selectOne(new LambdaQueryWrapper<MapDataVersionEntity>()
                .eq(MapDataVersionEntity::getParkId, parkId)
                .eq(MapDataVersionEntity::getIsActive, 1)
                .eq(MapDataVersionEntity::getDeleted, 0)
                .last("LIMIT 1"));
        return entity == null ? null : toVersionResponse(entity, parkMap.get(entity.getParkId()));
    }

    @Override
    @Transactional
    public AdminMapDataVersionResponse createMapVersion(AdminMapDataVersionUpsertRequest request) {
        if (request.getParkId() == null) {
            throw new BusinessException("PARK_REQUIRED", "园区ID不能为空");
        }
        if (request.getVersionCode() == null || request.getVersionCode().isBlank()) {
            throw new BusinessException("VERSION_CODE_REQUIRED", "版本编码不能为空");
        }
        ParkEntity park = parkMapper.selectById(request.getParkId());
        if (park == null || park.getDeleted() != null && park.getDeleted() != 0) {
            throw new BusinessException("PARK_NOT_FOUND", "园区不存在");
        }
        MapDataVersionEntity entity = new MapDataVersionEntity();
        entity.setParkId(request.getParkId());
        entity.setVersionCode(request.getVersionCode().trim());
        entity.setVersionLabel(request.getVersionLabel());
        entity.setRoadNodeCount(request.getRoadNodeCount());
        entity.setRoadSegmentCount(request.getRoadSegmentCount());
        entity.setStationCount(request.getStationCount());
        entity.setBuildingBlockCount(request.getBuildingBlockCount());
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(request.getPublishedBy());
        entity.setIsActive(request.getIsActive() != null && request.getIsActive() == 1 ? 1 : 0);
        entity.setChecksum(request.getChecksum());
        entity.setRemark(request.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        if (entity.getIsActive() == 1) {
            deactivateOtherVersions(entity.getParkId(), null);
        }
        mapDataVersionMapper.insert(entity);
        return toVersionResponse(entity, park);
    }

    @Override
    @Transactional
    public AdminMapDataVersionResponse activateMapVersion(Long versionId) {
        MapDataVersionEntity entity = requireMapVersion(versionId);
        ParkEntity park = parkMapper.selectById(entity.getParkId());
        if (park == null || park.getDeleted() != null && park.getDeleted() != 0) {
            throw new BusinessException("PARK_NOT_FOUND", "园区不存在");
        }
        deactivateOtherVersions(entity.getParkId(), versionId);
        entity.setIsActive(1);
        entity.setUpdatedAt(LocalDateTime.now());
        mapDataVersionMapper.updateById(entity);
        return toVersionResponse(entity, park);
    }

    // ===== 内部辅助方法 =====

    private Map<Long, ParkEntity> loadParkMap() {
        return parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>().eq(ParkEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(ParkEntity::getId, Function.identity()));
    }

    private void deactivateOtherVersions(Long parkId, Long exceptVersionId) {
        LambdaUpdateWrapper<MapDataVersionEntity> update = new LambdaUpdateWrapper<MapDataVersionEntity>()
                .eq(MapDataVersionEntity::getParkId, parkId)
                .eq(MapDataVersionEntity::getIsActive, 1)
                .eq(MapDataVersionEntity::getDeleted, 0)
                .set(MapDataVersionEntity::getIsActive, 0)
                .set(MapDataVersionEntity::getUpdatedAt, LocalDateTime.now());
        if (exceptVersionId != null) {
            update.ne(MapDataVersionEntity::getId, exceptVersionId);
        }
        mapDataVersionMapper.update(null, update);
    }

    private void applyFields(StationServicePositionEntity entity, AdminStationServicePositionUpsertRequest request) {
        entity.setPositionCode(request.getPositionCode());
        entity.setPositionName(request.getPositionName());
        entity.setCoordLng(request.getCoordLng());
        entity.setCoordLat(request.getCoordLat());
        entity.setCoordX(request.getCoordX());
        entity.setCoordY(request.getCoordY());
        entity.setAccessNodeCode(request.getAccessNodeCode());
        entity.setServiceDirection(request.getServiceDirection());
        entity.setAllowedVehicleTypes(request.getAllowedVehicleTypes());
        entity.setCapacityLimit(request.getCapacityLimit());
        entity.setRemark(request.getRemark());
    }

    private String resolveStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private StationEntity requireStation(Long stationId) {
        StationEntity station = stationMapper.selectById(stationId);
        if (station == null || station.getDeleted() != null && station.getDeleted() != 0) {
            throw new BusinessException("STATION_NOT_FOUND", "站点不存在");
        }
        return station;
    }

    private StationServicePositionEntity requirePosition(Long positionId) {
        StationServicePositionEntity entity = positionMapper.selectById(positionId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() != 0) {
            throw new BusinessException("SERVICE_POSITION_NOT_FOUND", "服务位不存在");
        }
        return entity;
    }

    private MapDataVersionEntity requireMapVersion(Long versionId) {
        MapDataVersionEntity entity = mapDataVersionMapper.selectById(versionId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() != 0) {
            throw new BusinessException("MAP_VERSION_NOT_FOUND", "地图版本不存在");
        }
        return entity;
    }

    private AdminStationServicePositionResponse toResponse(StationServicePositionEntity p, StationEntity station) {
        return AdminStationServicePositionResponse.builder()
                .id(p.getId())
                .stationId(p.getStationId())
                .stationCode(station != null ? station.getStationCode() : null)
                .stationName(station != null ? station.getStationName() : null)
                .positionCode(p.getPositionCode())
                .positionName(p.getPositionName())
                .coordLng(p.getCoordLng())
                .coordLat(p.getCoordLat())
                .coordX(p.getCoordX())
                .coordY(p.getCoordY())
                .accessNodeCode(p.getAccessNodeCode())
                .serviceDirection(p.getServiceDirection())
                .allowedVehicleTypes(p.getAllowedVehicleTypes())
                .capacityLimit(p.getCapacityLimit())
                .status(p.getStatus())
                .reservedVehicleId(p.getReservedVehicleId())
                .reservedUntil(p.getReservedUntil())
                .remark(p.getRemark())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private AdminMapDataVersionResponse toVersionResponse(MapDataVersionEntity v, ParkEntity park) {
        return AdminMapDataVersionResponse.builder()
                .id(v.getId())
                .parkId(v.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .versionCode(v.getVersionCode())
                .versionLabel(v.getVersionLabel())
                .roadNodeCount(v.getRoadNodeCount())
                .roadSegmentCount(v.getRoadSegmentCount())
                .stationCount(v.getStationCount())
                .buildingBlockCount(v.getBuildingBlockCount())
                .publishedAt(v.getPublishedAt())
                .publishedBy(v.getPublishedBy())
                .isActive(v.getIsActive())
                .checksum(v.getChecksum())
                .remark(v.getRemark())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
