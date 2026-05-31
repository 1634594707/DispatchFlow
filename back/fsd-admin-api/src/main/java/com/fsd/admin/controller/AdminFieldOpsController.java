package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminFieldOpsAssignRequest;
import com.fsd.admin.dto.AdminFieldOpsStatusUpdateRequest;
import com.fsd.admin.service.FieldOpsTicketAdminService;
import com.fsd.admin.vo.AdminFieldOpsTicketResponse;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@SecurityRequirement(name = "adminToken")
public class AdminFieldOpsController {

    private final FieldOpsTicketAdminService fieldOpsTicketAdminService;

    public AdminFieldOpsController(FieldOpsTicketAdminService fieldOpsTicketAdminService) {
        this.fieldOpsTicketAdminService = fieldOpsTicketAdminService;
    }

    @PostMapping("/exceptions/{exceptionId}/assign")
    @Operation(summary = "Assign exception to field ops", description = "Creates a field-ops ticket from a dispatch exception")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<AdminFieldOpsTicketResponse> assign(@PathVariable Long exceptionId,
                                                           @Valid @RequestBody AdminFieldOpsAssignRequest body,
                                                           HttpServletRequest request) {
        AdminAuthContext auth = AdminAuthSupport.requireAuth(request);
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(fieldOpsTicketAdminService.assignFromException(
                exceptionId, body.getAssigneeUserId(), body.getNotes(), auth.getUsername()));
    }

    @GetMapping("/tickets")
    @Operation(summary = "List field-ops tickets", description = "Filter by assignee or status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<List<AdminFieldOpsTicketResponse>> list(@RequestParam(required = false) Long assigneeUserId,
                                                               @RequestParam(required = false) String status,
                                                               HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(fieldOpsTicketAdminService.listTickets(assigneeUserId, status));
    }

    @PutMapping("/tickets/{ticketId}/status")
    @Operation(summary = "Update ticket status", description = "Transition a field-ops ticket to a new status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminFieldOpsTicketResponse> updateStatus(@PathVariable Long ticketId,
                                                                 @Valid @RequestBody AdminFieldOpsStatusUpdateRequest body,
                                                                 HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(fieldOpsTicketAdminService.updateStatus(
                ticketId, body.getStatus(), body.getNotes()));
    }
}
