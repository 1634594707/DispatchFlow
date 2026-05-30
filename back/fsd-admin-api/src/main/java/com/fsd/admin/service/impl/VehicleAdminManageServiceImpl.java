package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminVehicleMaintenanceUpsertRequest;
import com.fsd.admin.dto.AdminVehicleUpsertRequest;
import com.fsd.admin.service.VehicleAdminManageService;
import com.fsd.admin.vo.AdminVehicleCredentialResponse;
import com.fsd.admin.vo.AdminVehicleMaintenanceResponse;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.vehicle.entity.VehicleCredentialEntity;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.entity.VehicleMaintenanceEntity;
import com.fsd.vehicle.mapper.VehicleCredentialMapper;
import com.fsd.vehicle.mapper.VehicleMaintenanceMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleAdminManageServiceImpl implements VehicleAdminManageService {

    private final VehicleMapper vehicleMapper;
    private final VehicleCredentialMapper vehicleCredentialMapper;
    private final VehicleMaintenanceMapper vehicleMaintenanceMapper;
    private final VehicleAdminQueryService vehicleAdminQueryService;

    public VehicleAdminManageServiceImpl(VehicleMapper vehicleMapper,
                                         VehicleCredentialMapper vehicleCredentialMapper,
                                         VehicleMaintenanceMapper vehicleMaintenanceMapper,
                                         VehicleAdminQueryService vehicleAdminQueryService) {
        this.vehicleMapper = vehicleMapper;
        this.vehicleCredentialMapper = vehicleCredentialMapper;
        this.vehicleMaintenanceMapper = vehicleMaintenanceMapper;
        this.vehicleAdminQueryService = vehicleAdminQueryService;
    }

    @Override
    @Transactional
    public VehicleAdminDetailResponse createVehicle(AdminVehicleUpsertRequest request) {
        ensureUniqueCode(request.getVehicleCode(), null);
        VehicleEntity vehicle = new VehicleEntity();
        applyVehicleFields(vehicle, request);
        vehicle.setOnlineStatus(VehicleOnlineStatus.OFFLINE.name());
        vehicle.setDispatchStatus(VehicleDispatchStatus.UNAVAILABLE.name());
        vehicle.setDeleted(0);
        vehicle.setVersion(0);
        vehicleMapper.insert(vehicle);
        return vehicleAdminQueryService.getVehicleDetail(vehicle.getId());
    }

    @Override
    @Transactional
    public VehicleAdminDetailResponse updateVehicle(Long vehicleId, AdminVehicleUpsertRequest request) {
        VehicleEntity vehicle = requireVehicle(vehicleId);
        ensureUniqueCode(request.getVehicleCode(), vehicleId);
        applyVehicleFields(vehicle, request);
        vehicleMapper.updateById(vehicle);
        return vehicleAdminQueryService.getVehicleDetail(vehicleId);
    }

    @Override
    @Transactional
    public void disableVehicle(Long vehicleId) {
        VehicleEntity vehicle = requireVehicle(vehicleId);
        if (VehicleDispatchStatus.BUSY.name().equals(vehicle.getDispatchStatus())) {
            throw new BusinessException("VEHICLE_BUSY", "车辆正在执行任务，无法停用");
        }
        vehicle.setDeleted(1);
        vehicle.setDispatchStatus(VehicleDispatchStatus.UNAVAILABLE.name());
        vehicleMapper.updateById(vehicle);
        vehicleCredentialMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VehicleCredentialEntity>()
                .eq(VehicleCredentialEntity::getVehicleId, vehicleId)
                .set(VehicleCredentialEntity::getStatus, "DISABLED"));
    }

    @Override
    public List<AdminVehicleCredentialResponse> listCredentials(Long vehicleId) {
        requireVehicle(vehicleId);
        return vehicleCredentialMapper.selectList(new LambdaQueryWrapper<VehicleCredentialEntity>()
                        .eq(VehicleCredentialEntity::getVehicleId, vehicleId)
                        .orderByDesc(VehicleCredentialEntity::getCreatedAt))
                .stream()
                .map(this::toCredentialResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdminVehicleCredentialResponse createCredential(Long vehicleId) {
        requireVehicle(vehicleId);
        VehicleCredentialEntity credential = new VehicleCredentialEntity();
        credential.setVehicleId(vehicleId);
        credential.setApiKey("vk_" + UUID.randomUUID().toString().replace("-", ""));
        credential.setStatus("ACTIVE");
        vehicleCredentialMapper.insert(credential);
        return toCredentialResponse(credential);
    }

    @Override
    @Transactional
    public void disableCredential(Long credentialId) {
        VehicleCredentialEntity credential = vehicleCredentialMapper.selectById(credentialId);
        if (credential == null) {
            throw new BusinessException("VEHICLE_CREDENTIAL_NOT_FOUND", "凭证不存在");
        }
        credential.setStatus("DISABLED");
        vehicleCredentialMapper.updateById(credential);
    }

    @Override
    public List<AdminVehicleMaintenanceResponse> listMaintenanceRecords(Long vehicleId) {
        VehicleEntity vehicle = requireVehicle(vehicleId);
        return vehicleMaintenanceMapper.selectList(new LambdaQueryWrapper<VehicleMaintenanceEntity>()
                        .eq(VehicleMaintenanceEntity::getVehicleId, vehicleId)
                        .eq(VehicleMaintenanceEntity::getDeleted, 0)
                        .orderByDesc(VehicleMaintenanceEntity::getMaintenanceAt))
                .stream()
                .map(record -> toMaintenanceResponse(record, vehicle))
                .toList();
    }

    @Override
    @Transactional
    public AdminVehicleMaintenanceResponse createMaintenanceRecord(AdminVehicleMaintenanceUpsertRequest request,
                                                                   String operatorName) {
        VehicleEntity vehicle = requireVehicle(request.getVehicleId());
        validateMaintenanceType(request.getMaintenanceType());
        VehicleMaintenanceEntity entity = new VehicleMaintenanceEntity();
        entity.setVehicleId(request.getVehicleId());
        entity.setMaintenanceType(request.getMaintenanceType());
        entity.setDescription(request.getDescription().trim());
        entity.setMaintenanceAt(request.getMaintenanceAt());
        entity.setOperatorName(operatorName);
        entity.setStatus(request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus() : "COMPLETED");
        entity.setRemark(request.getRemark());
        entity.setDeleted(0);
        entity.setVersion(0);
        vehicleMaintenanceMapper.insert(entity);
        return toMaintenanceResponse(entity, vehicle);
    }

    private void applyVehicleFields(VehicleEntity vehicle, AdminVehicleUpsertRequest request) {
        vehicle.setVehicleCode(request.getVehicleCode().trim());
        vehicle.setVehicleName(request.getVehicleName().trim());
        vehicle.setVehicleType(request.getVehicleType() != null ? request.getVehicleType() : "GENERAL");
        String linkMode = request.getLinkMode();
        if (linkMode != null && !linkMode.isBlank()) {
            try {
                vehicle.setLinkMode(VehicleLinkMode.valueOf(linkMode).name());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("VEHICLE_LINK_MODE_INVALID", "无效的连接模式");
            }
        } else if (vehicle.getLinkMode() == null) {
            vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        }
        vehicle.setRemark(request.getRemark());
    }

    private void ensureUniqueCode(String vehicleCode, Long exceptId) {
        VehicleEntity existing = vehicleMapper.selectOne(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getVehicleCode, vehicleCode.trim())
                .eq(VehicleEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("VEHICLE_CODE_EXISTS", "车辆编码已存在");
        }
    }

    private VehicleEntity requireVehicle(Long vehicleId) {
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || vehicle.getDeleted() != null && vehicle.getDeleted() != 0) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "车辆不存在");
        }
        return vehicle;
    }

    private void validateMaintenanceType(String type) {
        if (!List.of("ROUTINE", "REPAIR", "INSPECTION", "PLANNED").contains(type)) {
            throw new BusinessException("MAINTENANCE_TYPE_INVALID", "无效的维护类型");
        }
    }

    private AdminVehicleCredentialResponse toCredentialResponse(VehicleCredentialEntity entity) {
        return AdminVehicleCredentialResponse.builder()
                .id(entity.getId())
                .vehicleId(entity.getVehicleId())
                .apiKey(entity.getApiKey())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AdminVehicleMaintenanceResponse toMaintenanceResponse(VehicleMaintenanceEntity entity,
                                                                    VehicleEntity vehicle) {
        return AdminVehicleMaintenanceResponse.builder()
                .id(entity.getId())
                .vehicleId(entity.getVehicleId())
                .vehicleCode(vehicle.getVehicleCode())
                .maintenanceType(entity.getMaintenanceType())
                .description(entity.getDescription())
                .maintenanceAt(entity.getMaintenanceAt())
                .operatorName(entity.getOperatorName())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
