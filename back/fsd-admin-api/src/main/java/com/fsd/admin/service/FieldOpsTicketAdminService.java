package com.fsd.admin.service;

import com.fsd.admin.vo.AdminFieldOpsTicketResponse;
import java.util.List;

public interface FieldOpsTicketAdminService {

    AdminFieldOpsTicketResponse assignFromException(Long exceptionId, Long assigneeUserId, String notes, String operator);

    default AdminFieldOpsTicketResponse assignFromException(Long exceptionId, Long assigneeUserId,
                                                             String notes, String operator, Long parkId) {
        return assignFromException(exceptionId, assigneeUserId, notes, operator);
    }

    List<AdminFieldOpsTicketResponse> listTickets(Long assigneeUserId, String status);

    default List<AdminFieldOpsTicketResponse> listTickets(Long assigneeUserId, String status, Long parkId) {
        return listTickets(assigneeUserId, status);
    }

    AdminFieldOpsTicketResponse updateStatus(Long ticketId, String status, String notes);

    default AdminFieldOpsTicketResponse updateStatus(Long ticketId, String status, String notes, Long parkId) {
        return updateStatus(ticketId, status, notes);
    }
}
