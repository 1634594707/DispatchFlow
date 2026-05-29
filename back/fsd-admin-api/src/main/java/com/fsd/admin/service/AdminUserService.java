package com.fsd.admin.service;

import com.fsd.admin.dto.AdminUserCreateRequest;
import com.fsd.admin.dto.AdminUserUpdateRequest;
import com.fsd.admin.vo.AdminUserResponse;
import java.util.List;

public interface AdminUserService {

    List<AdminUserResponse> listUsers();

    AdminUserResponse createUser(AdminUserCreateRequest request);

    AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request);

    void disableUser(Long userId);
}
