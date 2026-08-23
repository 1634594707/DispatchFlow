package com.fsd.admin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 路线图 Phase 4：统一权限资源表契约。
 * 资源 × 动作 × 角色矩阵的单一事实来源；未登记组合默认拒绝。
 */
class AdminPermissionMatrixTest {

    private final AdminPermissionService permissionService = new AdminPermissionService();

    @Test
    void taskMatrixShouldMatchContract() {
        // 读取：全员
        for (AdminRole role : AdminRole.values()) {
            assertTrue(permissionService.isAllowed(role, AdminResource.TASK, AdminAction.READ),
                    role + " 应可读取任务");
        }
        // 派车/取消：调度执行角色
        assertTrue(permissionService.isAllowed(AdminRole.OPERATOR, AdminResource.TASK, AdminAction.ASSIGN));
        assertTrue(permissionService.isAllowed(AdminRole.ADMIN, AdminResource.TASK, AdminAction.ASSIGN));
        assertFalse(permissionService.isAllowed(AdminRole.VIEWER, AdminResource.TASK, AdminAction.ASSIGN));
        assertFalse(permissionService.isAllowed(AdminRole.FIELD_OPS, AdminResource.TASK, AdminAction.ASSIGN));
        assertFalse(permissionService.isAllowed(AdminRole.VIEWER, AdminResource.TASK, AdminAction.CANCEL));
        assertFalse(permissionService.isAllowed(AdminRole.OPERATOR, AdminResource.TASK, AdminAction.WRITE),
                "任务资源未登记 WRITE，默认拒绝");
    }

    @Test
    void vehicleAndInfrastructureMatrixShouldMatchContract() {
        // 车辆读取：全员；管理写入：仅管理员
        for (AdminRole role : AdminRole.values()) {
            assertTrue(permissionService.isAllowed(role, AdminResource.VEHICLE, AdminAction.READ),
                    role + " 应可读取车辆");
        }
        assertTrue(permissionService.isAllowed(AdminRole.ADMIN, AdminResource.VEHICLE, AdminAction.WRITE));
        assertFalse(permissionService.isAllowed(AdminRole.OPERATOR, AdminResource.VEHICLE, AdminAction.WRITE));

        // 基础设施写入：仅管理员
        for (AdminRole role : AdminRole.values()) {
            assertEquals(role == AdminRole.ADMIN,
                    permissionService.isAllowed(role, AdminResource.INFRASTRUCTURE, AdminAction.WRITE),
                    role + " 的基础设施写权限不符合契约");
        }
    }

    @Test
    void analyticsExportShouldAllowAllAuthenticatedRoles() {
        for (AdminRole role : AdminRole.values()) {
            assertTrue(permissionService.isAllowed(role, AdminResource.ANALYTICS_EXPORT, AdminAction.EXECUTE),
                    role + " 应可导出分析数据");
        }
    }

    @Test
    void unknownCombinationsShouldDefaultDeny() {
        assertFalse(permissionService.isAllowed(AdminRole.ADMIN, AdminResource.TASK, AdminAction.EXECUTE));
        assertFalse(permissionService.isAllowed(null, AdminResource.TASK, AdminAction.READ));
        assertFalse(permissionService.isAllowed(AdminRole.ADMIN, null, AdminAction.READ));
    }

    @Test
    void checkShouldThrowForbiddenForInsufficientRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, "VIEWER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.check(request, AdminResource.INFRASTRUCTURE, AdminAction.WRITE));
        assertEquals("ADMIN_FORBIDDEN", ex.getCode());

        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, "ADMIN");
        permissionService.check(request, AdminResource.INFRASTRUCTURE, AdminAction.WRITE);
    }

    @Test
    void checkShouldRequireAuthentication() {
        MockHttpServletRequest anonymous = new MockHttpServletRequest();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.check(anonymous, AdminResource.TASK, AdminAction.READ));
        assertEquals("ADMIN_AUTH_REQUIRED", ex.getCode());
    }
}
