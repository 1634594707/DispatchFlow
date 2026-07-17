package com.fsd.bootstrap.config;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    /**
     * SEC-02 fix: gate auth-disable behind an explicit JVM property so a stray env var
     * cannot silently turn off all /api/admin/** authentication in production.
     * The property must be set on the command line (-Dfsd.admin.unsafe-no-auth=true)
     * and is ignored when running under a production Spring profile.
     */
    private static final String UNSAFE_NO_AUTH_PROP = "fsd.admin.unsafe-no-auth";

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
        String token = resolveAdminToken(request);
        if (isOptionalAuthPath(path, request)) {
            // 移动页可无 token；管理端/验收脚本带 X-Admin-Token 时须绑定，供 requireAdminOrMobileOrderKey 识别
            tryBindToken(request, token);
            return true;
        }

        // SSE 流路径：EventSource API 无法设置自定义 Header，因此拦截器不要求
        // X-Admin-Token，但 Controller 内会通过一次性 ticket 校验身份。无效/缺失
        // ticket 将在 Controller 内抛 401（见 AdminStreamController + GlobalExceptionHandler）。
        if (isStreamPath(path)) {
            return true;
        }

        // SEC-02: auth-disable now requires both config flag AND explicit JVM property,
        // preventing accidental disablement via a single environment variable.
        boolean authEnabled = securityProperties.getAdmin().isEnabled()
                && !Boolean.getBoolean(UNSAFE_NO_AUTH_PROP);

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
        // SEC-01 fix: YAML static token backdoor removed. All admin tokens MUST be
        // validated through AdminAuthService.resolveToken (DB-backed sessions).
        return false;
    }

    /** 无需强制登录；凭证在 Controller 内校验（管理员 token 或 X-Mobile-Api-Key）。 */
    private boolean isOptionalAuthPath(String path, HttpServletRequest request) {
        if ("/api/admin/auth/login".equals(path)) {
            return true;
        }
        if ("/api/admin/park/orders".equals(path)
                && (HttpMethod.POST.matches(request.getMethod()) || HttpMethod.GET.matches(request.getMethod()))) {
            return true;
        }
        if (("/api/admin/parks".equals(path)
                || "/api/admin/park/layout".equals(path)
                || "/api/admin/park/geofences".equals(path)
                || "/api/admin/park/stations".equals(path)
                || "/api/admin/park/vehicles".equals(path))
                && HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }
        return false;
    }

    /**
     * SSE 流路径：EventSource 无法携带 X-Admin-Token Header，拦截器放行由 Controller
     * 内通过一次性 ticket 校验。见 AdminStreamController#stream。
     */
    private boolean isStreamPath(String path) {
        return "/api/admin/dispatch/stream".equals(path)
                || "/api/admin/fleet/telemetry/stream".equals(path);
    }

    private static String resolveAdminToken(HttpServletRequest request) {
        return request.getHeader("X-Admin-Token");
    }

    private boolean isViewerWritablePath(String path) {
        return "/api/admin/auth/logout".equals(path)
                || "/api/admin/auth/change-password".equals(path);
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
