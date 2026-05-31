package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.FieldOpsTicketAdminService;
import com.fsd.admin.vo.AdminFieldOpsTicketResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/field-ops")
@Tag(name = "Field Ops", description = "On-site tickets and exception assignment")
public class AdminFieldOpsController {

    private final FieldOpsTicketAdminService fieldOpsTicketAdminService;

    public AdminFieldOpsController(FieldOpsTicketAdminService fieldOpsTicketAdminService) {
        this.fieldOpsTicketAdminService = fieldOpsTicketAdminService;
    }

    @PostMapping("/exceptions/{exceptionId}/assign")
    public ApiResponse<AdminFieldOpsTicketResponse> assign(@PathVariable Long exceptionId,
                                                           @RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        AdminAuthContext auth = AdminAuthSupport.requireAuth(request);
        AdminAuthSupport.requireAdmin(request);
        Long assigneeUserId = body.get("assigneeUserId") instanceof Number n ? n.longValue() : null;
        String notes = body.get("notes") == null ? null : String.valueOf(body.get("notes"));
        return ApiResponse.success(fieldOpsTicketAdminService.assignFromException(
                exceptionId, assigneeUserId, notes, auth.getUsername()));
    }

    @GetMapping("/tickets")
    public ApiResponse<List<AdminFieldOpsTicketResponse>> list(@RequestParam(required = false) Long assigneeUserId,
                                                               @RequestParam(required = false) String status,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(fieldOpsTicketAdminService.listTickets(assigneeUserId, status));
    }

    @PutMapping("/tickets/{ticketId}/status")
    public ApiResponse<AdminFieldOpsTicketResponse> updateStatus(@PathVariable Long ticketId,
                                                                 @RequestBody Map<String, String> body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(fieldOpsTicketAdminService.updateStatus(
                ticketId, body.get("status"), body.get("notes")));
    }
}
