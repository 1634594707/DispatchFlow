package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminUserCreateRequest;
import com.fsd.admin.dto.AdminUserUpdateRequest;
import com.fsd.admin.entity.AdminSessionEntity;
import com.fsd.admin.entity.AdminUserEntity;
import com.fsd.admin.mapper.AdminSessionMapper;
import com.fsd.admin.mapper.AdminUserMapper;
import com.fsd.admin.service.AdminUserService;
import com.fsd.admin.vo.AdminUserResponse;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.enums.AdminUserStatus;
import com.fsd.common.exception.BusinessException;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper, AdminSessionMapper adminSessionMapper) {
        this.adminUserMapper = adminUserMapper;
        this.adminSessionMapper = adminSessionMapper;
    }

    @Override
    public List<AdminUserResponse> listUsers() {
        return adminUserMapper.selectList(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getDeleted, 0)
                        .orderByAsc(AdminUserEntity::getId))
                .stream()
                .map(AdminAuthServiceImpl::toUserResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdminUserResponse createUser(AdminUserCreateRequest request) {
        validateRole(request.getRole());
        Long exists = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, request.getUsername())
                .eq(AdminUserEntity::getDeleted, 0));
        if (exists != null && exists > 0) {
            throw new BusinessException("ADMIN_USERNAME_EXISTS", "用户名已存在");
        }

        AdminUserEntity user = new AdminUserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setRole(request.getRole());
        user.setStatus(AdminUserStatus.ACTIVE.name());
        user.setDeleted(0);
        user.setVersion(0);
        adminUserMapper.insert(user);
        return AdminAuthServiceImpl.toUserResponse(user);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request) {
        AdminUserEntity user = requireUser(userId);
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            validateRole(request.getRole());
            user.setRole(request.getRole());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            validateStatus(request.getStatus());
            user.setStatus(request.getStatus());
            if (AdminUserStatus.DISABLED.name().equals(request.getStatus())) {
                revokeSessions(userId);
            }
        }
        adminUserMapper.updateById(user);
        return AdminAuthServiceImpl.toUserResponse(user);
    }

    @Override
    @Transactional
    public void disableUser(Long userId) {
        AdminUserEntity user = requireUser(userId);
        user.setStatus(AdminUserStatus.DISABLED.name());
        adminUserMapper.updateById(user);
        revokeSessions(userId);
    }

    private AdminUserEntity requireUser(Long userId) {
        AdminUserEntity user = adminUserMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            throw new BusinessException("ADMIN_USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private void revokeSessions(Long userId) {
        adminSessionMapper.delete(new LambdaQueryWrapper<AdminSessionEntity>()
                .eq(AdminSessionEntity::getUserId, userId));
    }

    private void validateRole(String role) {
        try {
            AdminRole.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ADMIN_ROLE_INVALID", "无效的角色: " + role);
        }
    }

    private void validateStatus(String status) {
        try {
            AdminUserStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ADMIN_STATUS_INVALID", "无效的状态: " + status);
        }
    }
}
