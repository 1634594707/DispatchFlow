package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.config.AdminSseProperties;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AdminSseTicketServiceImplTest {

    @Test
    void ticketShouldBeSingleUse() {
        AdminSseProperties properties = new AdminSseProperties();
        AdminSseTicketServiceImpl service = new AdminSseTicketServiceImpl(properties);
        String ticket = service.issue(context());

        assertEquals("admin", service.consume(ticket).getUsername());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.consume(ticket));
        assertEquals("ADMIN_SSE_TICKET_INVALID", ex.getCode());
    }

    @Test
    void expiredTicketShouldBeRejected() throws InterruptedException {
        AdminSseProperties properties = new AdminSseProperties();
        properties.setTicketTtlSeconds(0L);
        AdminSseTicketServiceImpl service = new AdminSseTicketServiceImpl(properties);
        String ticket = service.issue(context());

        Thread.sleep(5L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.consume(ticket));
        assertTrue("ADMIN_SSE_TICKET_INVALID".equals(ex.getCode())
                || "ADMIN_SSE_TICKET_REQUIRED".equals(ex.getCode()));
    }

    private AdminAuthContext context() {
        return AdminAuthContext.builder()
                .userId(1L)
                .username("admin")
                .displayName("Admin")
                .role(AdminRole.ADMIN)
                .build();
    }
}
