package com.fsd.bootstrap.config;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final SecurityProperties securityProperties;
    private final AdminAuthService adminAuthService;

    public AdminAuthInterceptor(SecurityProperties securityProperties, AdminAuthService adminAuthService) {
        this.securityProperties = securityProperties;
        this.adminAuthService = adminAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) {
            return true;
        }
        if (isPublicPath(path)) {
            return true;
        }

        String token = request.getHeader("X-Admin-Token");
        boolean authEnabled = securityProperties.getAdmin().isEnabled();

        // Auth disabled: still bind user from token when present (supports frontend login + /auth/me)
        if (!authEnabled) {
            tryBindToken(request, token);
            return true;
        }

        if (token == null || token.isBlank()) {
            throw new BusinessException("ADMIN_AUTH_REQUIRED", "请先登录");
        }
        if (!tryBindToken(request, token)) {
            throw new BusinessException("ADMIN_AUTH_FAILED", "登录已失效，请重新登录");
        }

        AdminRole role = AdminRole.valueOf(request.getAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE).toString());
        if (isWriteMethod(request.getMethod()) && role == AdminRole.VIEWER && !isViewerWritablePath(path)) {
            throw new BusinessException("ADMIN_FORBIDDEN", "观察员账号无权执行写操作");
        }
        return true;
    }

    private boolean tryBindToken(HttpServletRequest request, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        AdminAuthContext context = adminAuthService.resolveToken(token);
        if (context != null) {
            bindRequest(request, context.getUserId(), context.getUsername(),
                    context.getDisplayName(), context.getRole());
            return true;
        }
        String yamlRole = resolveYamlRole(token);
        if (yamlRole == null) {
            return false;
        }
        bindRequest(request, null, null, null, AdminRole.valueOf(yamlRole));
        return true;
    }

    private boolean isPublicPath(String path) {
        return "/api/admin/auth/login".equals(path);
    }

    private boolean isViewerWritablePath(String path) {
        return "/api/admin/auth/logout".equals(path)
                || "/api/admin/auth/change-password".equals(path);
    }

    private String resolveYamlRole(String token) {
        Map<String, String> tokens = securityProperties.getAdmin().getTokens();
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return tokens.get(token);
    }

    private void bindRequest(HttpServletRequest request, Long userId, String username,
                             String displayName, AdminRole role) {
        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, role.name());
        request.setAttribute(AdminAuthSupport.ADMIN_USER_ID_ATTRIBUTE, userId);
        request.setAttribute(AdminAuthSupport.ADMIN_USERNAME_ATTRIBUTE, username);
        request.setAttribute(AdminAuthSupport.ADMIN_DISPLAY_NAME_ATTRIBUTE, displayName);
    }

    private boolean isWriteMethod(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }
}
