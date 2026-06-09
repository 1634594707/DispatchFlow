package com.fsd.bootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.common.enums.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private AdminAuthService adminAuthService;

    private SecurityProperties securityProperties;
    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getAdmin().setEnabled(true);
        interceptor = new AdminAuthInterceptor(securityProperties, adminAuthService);
    }

    @Test
    void optionalParkStationsPathShouldBindAdminTokenWhenPresent() {
        AdminAuthContext context = AdminAuthContext.builder()
                .userId(1L)
                .username("admin")
                .displayName("Admin")
                .role(AdminRole.ADMIN)
                .build();
        when(adminAuthService.resolveToken("token-abc")).thenReturn(context);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/park/stations");
        request.addHeader("X-Admin-Token", "token-abc");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertEquals(true, allowed);
        assertNotNull(request.getAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE));
        assertEquals("ADMIN", request.getAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE));
    }

    @Test
    void optionalParkStationsPathShouldAllowAnonymousWhenNoToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/park/stations");
        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertEquals(true, allowed);
        assertNull(request.getAttribute(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE));
    }

    @Test
    void requiredAdminPathShouldIgnoreQueryToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/tasks");
        request.setParameter("token", "token-abc");

        com.fsd.common.exception.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.fsd.common.exception.BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals("ADMIN_AUTH_REQUIRED", ex.getCode());
    }
}
