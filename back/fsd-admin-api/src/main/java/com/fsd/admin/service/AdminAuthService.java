package com.fsd.admin.service;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.dto.AdminChangePasswordRequest;
import com.fsd.admin.dto.AdminLoginRequest;
import com.fsd.admin.vo.AdminLoginResponse;
import com.fsd.admin.vo.AdminUserResponse;

public interface AdminAuthService {

    AdminLoginResponse login(AdminLoginRequest request);

    void logout(String token);

    AdminAuthContext resolveToken(String token);

    AdminUserResponse getCurrentUser(Long userId);

    void changePassword(Long userId, AdminChangePasswordRequest request);

    java.util.Map<String, String> enrollTotp(Long userId);

    void enableTotp(Long userId, String code);

    void disableTotp(Long userId, String code);
}
