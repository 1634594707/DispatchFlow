package com.fsd.admin.auth;

import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

public final class AdminAuthSupport {

    public static final String ADMIN_ROLE_ATTRIBUTE = "fsd.admin.role";
    public static final String ADMIN_USER_ID_ATTRIBUTE = "fsd.admin.userId";
    public static final String ADMIN_USERNAME_ATTRIBUTE = "fsd.admin.username";
    public static final String ADMIN_DISPLAY_NAME_ATTRIBUTE = "fsd.admin.displayName";

    private AdminAuthSupport() {
    }

    public static AdminAuthContext fromRequest(HttpServletRequest request) {
        Object roleObj = request.getAttribute(ADMIN_ROLE_ATTRIBUTE);
        if (roleObj == null) {
            return null;
        }
        return AdminAuthContext.builder()
                .userId((Long) request.getAttribute(ADMIN_USER_ID_ATTRIBUTE))
                .username((String) request.getAttribute(ADMIN_USERNAME_ATTRIBUTE))
                .displayName((String) request.getAttribute(ADMIN_DISPLAY_NAME_ATTRIBUTE))
                .role(AdminRole.valueOf(roleObj.toString()))
                .build();
    }

    public static AdminAuthContext requireAuth(HttpServletRequest request) {
        AdminAuthContext context = fromRequest(request);
        if (context == null) {
            throw new BusinessException("ADMIN_AUTH_REQUIRED", "请先登录");
        }
        return context;
    }

    public static void requireAdmin(HttpServletRequest request) {
        AdminAuthContext context = requireAuth(request);
        if (context.getRole() != AdminRole.ADMIN) {
            throw new BusinessException("ADMIN_FORBIDDEN", "Admin role is required");
        }
    }
}
