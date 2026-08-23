package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.admin.entity.AdminUserEntity;
import com.fsd.admin.service.FieldOpsTicketAdminService;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.FieldOpsTicketEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.FieldOpsTicketMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.admin.mapper.AdminUserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FieldOpsTicketAdminServiceImplTest {

    private FieldOpsTicketMapper ticketMapper;
    private DispatchExceptionRecordMapper exceptionRecordMapper;
    private AdminUserMapper adminUserMapper;
    private OrderMapper orderMapper;
    private FieldOpsTicketAdminService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(FieldOpsTicketMapper.class);
        exceptionRecordMapper = mock(DispatchExceptionRecordMapper.class);
        adminUserMapper = mock(AdminUserMapper.class);
        orderMapper = mock(OrderMapper.class);
        service = new FieldOpsTicketAdminServiceImpl(
                ticketMapper, exceptionRecordMapper, adminUserMapper,
                mock(DispatchExceptionService.class), orderMapper);
    }

    @Test
    void listTicketsShouldHideTicketsFromOtherParks() {
        FieldOpsTicketEntity visible = ticket(11L, 101L);
        FieldOpsTicketEntity hidden = ticket(12L, 102L);
        when(ticketMapper.selectList(any())).thenReturn(List.of(visible, hidden));
        when(exceptionRecordMapper.selectById(101L)).thenReturn(exception(101L, 1001L));
        when(exceptionRecordMapper.selectById(102L)).thenReturn(exception(102L, 1002L));
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 7L));
        when(orderMapper.selectById(1002L)).thenReturn(order(1002L, 8L));
        when(adminUserMapper.selectById(1L)).thenReturn(user(1L));

        assertEquals(List.of(11L), service.listTickets(null, null, 7L).stream()
                .map(response -> response.getId()).toList());
    }

    @Test
    void assignFromExceptionShouldRejectOtherPark() {
        when(exceptionRecordMapper.selectById(101L)).thenReturn(exception(101L, 1001L));
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 7L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assignFromException(101L, 1L, null, "admin", 8L));

        assertEquals("PARK_SCOPE_DENIED", ex.getCode());
    }

    @Test
    void updateStatusShouldRejectOtherPark() {
        FieldOpsTicketEntity ticket = ticket(11L, 101L);
        when(ticketMapper.selectById(11L)).thenReturn(ticket);
        when(exceptionRecordMapper.selectById(101L)).thenReturn(exception(101L, 1001L));
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 7L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus(11L, "DONE", null, 8L));

        assertEquals("PARK_SCOPE_DENIED", ex.getCode());
    }

    private FieldOpsTicketEntity ticket(Long id, Long exceptionId) {
        FieldOpsTicketEntity ticket = new FieldOpsTicketEntity();
        ticket.setId(id);
        ticket.setExceptionId(exceptionId);
        ticket.setAssigneeUserId(1L);
        ticket.setStatus("OPEN");
        return ticket;
    }

    private DispatchExceptionRecordEntity exception(Long id, Long orderId) {
        DispatchExceptionRecordEntity exception = new DispatchExceptionRecordEntity();
        exception.setId(id);
        exception.setOrderId(orderId);
        exception.setExceptionType("TEST");
        exception.setExceptionMsg("test");
        return exception;
    }

    private OrderEntity order(Long id, Long parkId) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setParkId(parkId);
        return order;
    }

    private AdminUserEntity user(Long id) {
        AdminUserEntity user = new AdminUserEntity();
        user.setId(id);
        user.setDisplayName("Operator");
        return user;
    }
}
