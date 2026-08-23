package com.fsd.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.auth.AdminPermissionService;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 路线图 3.1：导出权限测试 —— Viewer / Operator / Admin 三种角色的导出边界。
 *
 * <p>导出接口要求已认证（requireAuth），三种管理端角色均允许导出；
 * 未认证请求一律拒绝（ADMIN_AUTH_REQUIRED），导出不接受匿名访问。</p>
 */
class AdminAnalyticsControllerExportRoleTest {

    private AnalyticsAdminService analyticsAdminService;
    private AdminAnalyticsController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        analyticsAdminService = mock(AnalyticsAdminService.class);
        controller = new AdminAnalyticsController(analyticsAdminService, new AdminPermissionService());
        response = new MockHttpServletResponse();
        when(analyticsAdminService.exportCsv(anyString(), anyString(), anyLong())).thenReturn("order_no\nO1\n");
    }

    @Test
    void viewerShouldBeAbleToExport() throws Exception {
        assertEquals("csv-viewer", exportAs(AdminRole.VIEWER));
    }

    @Test
    void operatorShouldBeAbleToExport() throws Exception {
        assertEquals("csv-operator", exportAs(AdminRole.OPERATOR));
    }

    @Test
    void adminShouldBeAbleToExport() throws Exception {
        assertEquals("csv-admin", exportAs(AdminRole.ADMIN));
    }

    @Test
    void anonymousRequestShouldBeRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.exportCsv("orders", "week", null, request, response));
        assertEquals("ADMIN_AUTH_REQUIRED", ex.getCode());
    }

    /** 导出内容必须与筛选条件一致：园区与时间范围透传给服务层。 */
    @Test
    void exportShouldForwardParkAndPeriodFilters() throws Exception {
        analyticsAdminService = mock(AnalyticsAdminService.class);
        when(analyticsAdminService.exportCsv("tasks", "day", 7L)).thenReturn("filtered");
        controller = new AdminAnalyticsController(analyticsAdminService, new AdminPermissionService());

        MockHttpServletRequest request = authenticatedRequest(AdminRole.OPERATOR);
        controller.exportCsv("tasks", "day", 7L, request, response);

        assertEquals("filtered", response.getContentAsString());
    }

    private String exportAs(AdminRole role) throws Exception {
        analyticsAdminService = mock(AnalyticsAdminService.class);
        String csv = "csv-" + role.name().toLowerCase();
        when(analyticsAdminService.exportCsv(anyString(), anyString(), anyLong())).thenReturn(csv);
        controller = new AdminAnalyticsController(analyticsAdminService, new AdminPermissionService());

        MockHttpServletRequest request = authenticatedRequest(role);
        controller.exportCsv("orders", "week", 3L, request, response);

        assertEquals("text/csv; charset=UTF-8", response.getContentType());
        return csv;
    }

    private MockHttpServletRequest authenticatedRequest(AdminRole role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, role.name());
        request.setAttribute(AdminAuthSupport.ADMIN_USER_ID_ATTRIBUTE, 1L);
        request.setAttribute(AdminAuthSupport.ADMIN_USERNAME_ATTRIBUTE, "user-" + role.name().toLowerCase());
        return request;
    }
}
