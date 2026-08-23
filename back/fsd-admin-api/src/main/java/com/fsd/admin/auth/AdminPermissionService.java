package com.fsd.admin.auth;

import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 统一权限资源表（路线图 Phase 4）：后端授权以「资源 + 动作」判定，页面路由只用于隐藏入口。
 * 园区范围由各接口的 ensureXxxPark 逻辑继续约束。
 *
 * <p>未在矩阵中登记的资源/动作组合一律默认拒绝。</p>
 */
@Component
public class AdminPermissionService {

    private static final Set<AdminRole> ALL_ROLES = EnumSet.allOf(AdminRole.class);
    private static final Set<AdminRole> OPERATE_ROLES = EnumSet.of(AdminRole.OPERATOR, AdminRole.ADMIN);

    /** 资源 × 动作 → 允许角色。 */
    private static final Map<AdminResource, Map<AdminAction, Set<AdminRole>>> MATRIX = buildMatrix();

    private static Map<AdminResource, Map<AdminAction, Set<AdminRole>>> buildMatrix() {
        Map<AdminResource, Map<AdminAction, Set<AdminRole>>> matrix = new EnumMap<>(AdminResource.class);
        // 任务：读取全员；派车与取消仅调度执行角色
        matrix.put(AdminResource.TASK, actionMap(
                entry(AdminAction.READ, ALL_ROLES),
                entry(AdminAction.ASSIGN, OPERATE_ROLES),
                entry(AdminAction.CANCEL, OPERATE_ROLES)));
        // 车辆：读取全员；管理写入仅管理员
        matrix.put(AdminResource.VEHICLE, actionMap(
                entry(AdminAction.READ, ALL_ROLES),
                entry(AdminAction.WRITE, EnumSet.of(AdminRole.ADMIN))));
        // 基础设施：读取全员；写入仅管理员
        matrix.put(AdminResource.INFRASTRUCTURE, actionMap(
                entry(AdminAction.READ, ALL_ROLES),
                entry(AdminAction.WRITE, EnumSet.of(AdminRole.ADMIN))));
        // 分析导出：任意已认证角色（保持既有行为并显式登记）
        matrix.put(AdminResource.ANALYTICS_EXPORT, actionMap(
                entry(AdminAction.EXECUTE, ALL_ROLES)));
        return matrix;
    }

    private static Map<AdminAction, Set<AdminRole>> actionMap(
            Map.Entry<AdminAction, Set<AdminRole>>... entries) {
        Map<AdminAction, Set<AdminRole>> map = new EnumMap<>(AdminAction.class);
        for (Map.Entry<AdminAction, Set<AdminRole>> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static Map.Entry<AdminAction, Set<AdminRole>> entry(AdminAction action, Set<AdminRole> roles) {
        return Map.entry(action, roles);
    }

    /** 判定角色是否允许对资源执行动作；未登记组合默认拒绝。 */
    public boolean isAllowed(AdminRole role, AdminResource resource, AdminAction action) {
        if (role == null || resource == null || action == null) {
            return false;
        }
        Set<AdminRole> allowed = MATRIX.getOrDefault(resource, Map.of()).get(action);
        return allowed != null && allowed.contains(role);
    }

    /** 校验当前请求身份对资源动作的权限，不通过抛 ADMIN_FORBIDDEN。 */
    public void check(HttpServletRequest request, AdminResource resource, AdminAction action) {
        AdminAuthContext context = AdminAuthSupport.requireAuth(request);
        if (!isAllowed(context.getRole(), resource, action)) {
            throw new BusinessException("ADMIN_FORBIDDEN",
                    "当前角色无权执行该操作：「" + resource + "." + action + "」需要更高权限");
        }
    }
}