package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.geo.GeoPolygonUtils;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.service.ParkGeofenceService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.ParkGeofenceResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkGeofenceServiceImpl implements ParkGeofenceService {

    private static final TypeReference<List<List<BigDecimal>>> POLYGON_TYPE = new TypeReference<>() {
    };

    private final ParkGeofenceMapper geofenceMapper;
    private final ParkStationService parkStationService;
    private final ObjectMapper objectMapper;

    public ParkGeofenceServiceImpl(ParkGeofenceMapper geofenceMapper,
                                   ParkStationService parkStationService,
                                   ObjectMapper objectMapper) {
        this.geofenceMapper = geofenceMapper;
        this.parkStationService = parkStationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ParkGeofenceResponse> listByPark(Long parkId) {
        parkStationService.requirePark(parkId);
        return geofenceMapper.selectList(activeWrapper(parkId, false)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ParkGeofenceResponse> listActiveByPark(Long parkId) {
        if (parkId == null) {
            return List.of();
        }
        return geofenceMapper.selectList(activeWrapper(parkId, true)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ParkGeofenceResponse requireById(Long geofenceId) {
        ParkGeofenceEntity entity = geofenceMapper.selectOne(new LambdaQueryWrapper<ParkGeofenceEntity>()
                .eq(ParkGeofenceEntity::getId, geofenceId)
                .eq(ParkGeofenceEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException("GEOFENCE_NOT_FOUND", "Geofence not found");
        }
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ParkGeofenceResponse create(Long parkId,
                                       String fenceCode,
                                       String fenceName,
                                       String fenceType,
                                       String polygonJson,
                                       String remark) {
        parkStationService.requirePark(parkId);
        validateFenceType(fenceType);
        validatePolygonJson(polygonJson);
        ensureUniqueCode(parkId, fenceCode, null);
        ParkGeofenceEntity entity = new ParkGeofenceEntity();
        entity.setParkId(parkId);
        entity.setFenceCode(fenceCode.trim());
        entity.setFenceName(fenceName.trim());
        entity.setFenceType(normalizeFenceType(fenceType));
        entity.setPolygonJson(polygonJson);
        entity.setStatus("ACTIVE");
        entity.setRemark(remark);
        entity.setDeleted(0);
        entity.setVersion(0);
        geofenceMapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ParkGeofenceResponse update(Long geofenceId,
                                       String fenceName,
                                       String fenceType,
                                       String polygonJson,
                                       String status,
                                       String remark) {
        ParkGeofenceEntity entity = requireEntity(geofenceId);
        if (fenceName != null && !fenceName.isBlank()) {
            entity.setFenceName(fenceName.trim());
        }
        if (fenceType != null && !fenceType.isBlank()) {
            validateFenceType(fenceType);
            entity.setFenceType(normalizeFenceType(fenceType));
        }
        if (polygonJson != null && !polygonJson.isBlank()) {
            validatePolygonJson(polygonJson);
            entity.setPolygonJson(polygonJson);
        }
        if (status != null && !status.isBlank()) {
            entity.setStatus(status.trim().toUpperCase());
        }
        if (remark != null) {
            entity.setRemark(remark);
        }
        geofenceMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long geofenceId) {
        ParkGeofenceEntity entity = requireEntity(geofenceId);
        entity.setDeleted(1);
        geofenceMapper.updateById(entity);
    }

    @Override
    public boolean isInsideFence(String fenceType, String polygonJson, BigDecimal longitude, BigDecimal latitude) {
        return GeoPolygonUtils.contains(parsePolygonVertices(polygonJson), longitude, latitude);
    }

    private ParkGeofenceEntity requireEntity(Long geofenceId) {
        ParkGeofenceEntity entity = geofenceMapper.selectOne(new LambdaQueryWrapper<ParkGeofenceEntity>()
                .eq(ParkGeofenceEntity::getId, geofenceId)
                .eq(ParkGeofenceEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException("GEOFENCE_NOT_FOUND", "Geofence not found");
        }
        return entity;
    }

    private LambdaQueryWrapper<ParkGeofenceEntity> activeWrapper(Long parkId, boolean activeOnly) {
        LambdaQueryWrapper<ParkGeofenceEntity> wrapper = new LambdaQueryWrapper<ParkGeofenceEntity>()
                .eq(ParkGeofenceEntity::getParkId, parkId)
                .eq(ParkGeofenceEntity::getDeleted, 0)
                .orderByAsc(ParkGeofenceEntity::getId);
        if (activeOnly) {
            wrapper.eq(ParkGeofenceEntity::getStatus, "ACTIVE");
        }
        return wrapper;
    }

    private void ensureUniqueCode(Long parkId, String fenceCode, Long excludeId) {
        LambdaQueryWrapper<ParkGeofenceEntity> wrapper = new LambdaQueryWrapper<ParkGeofenceEntity>()
                .eq(ParkGeofenceEntity::getParkId, parkId)
                .eq(ParkGeofenceEntity::getFenceCode, fenceCode.trim())
                .eq(ParkGeofenceEntity::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(ParkGeofenceEntity::getId, excludeId);
        }
        if (geofenceMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("GEOFENCE_CODE_DUPLICATE", "Geofence code already exists in park");
        }
    }

    private void validateFenceType(String fenceType) {
        String normalized = normalizeFenceType(fenceType);
        if (!"BOUNDARY".equals(normalized) && !"RESTRICTED".equals(normalized)) {
            throw new BusinessException("GEOFENCE_TYPE_INVALID", "fenceType must be BOUNDARY or RESTRICTED");
        }
    }

    private static String normalizeFenceType(String fenceType) {
        return fenceType == null ? "BOUNDARY" : fenceType.trim().toUpperCase();
    }

    private void validatePolygonJson(String polygonJson) {
        List<List<BigDecimal>> polygon = readPolygon(polygonJson);
        if (polygon.size() < 3) {
            throw new BusinessException("GEOFENCE_POLYGON_INVALID", "Polygon requires at least 3 vertices");
        }
        for (List<BigDecimal> point : polygon) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                throw new BusinessException("GEOFENCE_POLYGON_INVALID", "Each vertex must be [lng, lat]");
            }
        }
    }

    private ParkGeofenceResponse toResponse(ParkGeofenceEntity entity) {
        return ParkGeofenceResponse.builder()
                .id(entity.getId())
                .parkId(entity.getParkId())
                .fenceCode(entity.getFenceCode())
                .fenceName(entity.getFenceName())
                .fenceType(entity.getFenceType())
                .polygon(readPolygon(entity.getPolygonJson()))
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<List<BigDecimal>> readPolygon(String polygonJson) {
        if (polygonJson == null || polygonJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(polygonJson, POLYGON_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("GEOFENCE_POLYGON_INVALID", ex.getMessage());
        }
    }

    private List<double[]> parsePolygonVertices(String polygonJson) {
        List<double[]> vertices = new ArrayList<>();
        for (List<BigDecimal> point : readPolygon(polygonJson)) {
            vertices.add(new double[] {point.get(0).doubleValue(), point.get(1).doubleValue()});
        }
        return vertices;
    }
}
