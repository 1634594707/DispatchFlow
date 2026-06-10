package com.fsd.admin.service;

import com.fsd.admin.vo.AdminFieldOpsTicketResponse;
import java.util.List;

public interface FieldOpsTicketAdminService {

    AdminFieldOpsTicketResponse assignFromException(Long exceptionId, Long assigneeUserId, String notes, String operator);

    List<AdminFieldOpsTicketResponse> listTickets(Long assigneeUserId, String status);

    AdminFieldOpsTicketResponse updateStatus(Long ticketId, String status, String notes);
}
