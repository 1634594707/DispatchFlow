package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.entity.AdminUserEntity;
import com.fsd.admin.mapper.AdminUserMapper;
import com.fsd.admin.service.FieldOpsTicketAdminService;
import com.fsd.admin.vo.AdminFieldOpsTicketResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.FieldOpsTicketEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.FieldOpsTicketMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldOpsTicketAdminServiceImpl implements FieldOpsTicketAdminService {

    private final FieldOpsTicketMapper ticketMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final AdminUserMapper adminUserMapper;
    private final DispatchExceptionService dispatchExceptionService;

    public FieldOpsTicketAdminServiceImpl(FieldOpsTicketMapper ticketMapper,
                                          DispatchExceptionRecordMapper exceptionRecordMapper,
                                          AdminUserMapper adminUserMapper,
                                          DispatchExceptionService dispatchExceptionService) {
        this.ticketMapper = ticketMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.adminUserMapper = adminUserMapper;
        this.dispatchExceptionService = dispatchExceptionService;
    }

    @Override
    @Transactional
    public AdminFieldOpsTicketResponse assignFromException(Long exceptionId, Long assigneeUserId,
                                                           String notes, String operator) {
        DispatchExceptionRecordEntity exception = exceptionRecordMapper.selectById(exceptionId);
        if (exception == null) {
            throw new BusinessException("EXCEPTION_NOT_FOUND", "异常记录不存在");
        }
        AdminUserEntity assignee = adminUserMapper.selectById(assigneeUserId);
        if (assignee == null || assignee.getDeleted() != null && assignee.getDeleted() != 0) {
            throw new BusinessException("ASSIGNEE_NOT_FOUND", "指派用户不存在");
        }
        FieldOpsTicketEntity ticket = new FieldOpsTicketEntity();
        ticket.setExceptionId(exceptionId);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setStatus("OPEN");
        ticket.setNotes(notes);
        ticket.setCreatedBy(operator);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);
        return toResponse(ticket, exception, assignee);
    }

    @Override
    public List<AdminFieldOpsTicketResponse> listTickets(Long assigneeUserId, String status) {
        LambdaQueryWrapper<FieldOpsTicketEntity> query = new LambdaQueryWrapper<FieldOpsTicketEntity>()
                .orderByDesc(FieldOpsTicketEntity::getCreatedAt);
        if (assigneeUserId != null) {
            query.eq(FieldOpsTicketEntity::getAssigneeUserId, assigneeUserId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(FieldOpsTicketEntity::getStatus, status);
        }
        return ticketMapper.selectList(query).stream()
                .map(ticket -> {
                    DispatchExceptionRecordEntity exception = exceptionRecordMapper.selectById(ticket.getExceptionId());
                    AdminUserEntity assignee = adminUserMapper.selectById(ticket.getAssigneeUserId());
                    return toResponse(ticket, exception, assignee);
                })
                .toList();
    }

    @Override
    @Transactional
    public AdminFieldOpsTicketResponse updateStatus(Long ticketId, String status, String notes) {
        FieldOpsTicketEntity ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("TICKET_NOT_FOUND", "工单不存在");
        }
        if (status != null && !status.isBlank()) {
            validateStatusTransition(ticket.getStatus(), status);
            ticket.setStatus(status);
        }
        if (notes != null) {
            ticket.setNotes(notes);
        }
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        if ("DONE".equalsIgnoreCase(ticket.getStatus()) && ticket.getExceptionId() != null) {
            resolveLinkedException(ticket.getExceptionId(), notes);
        }
        DispatchExceptionRecordEntity exception = exceptionRecordMapper.selectById(ticket.getExceptionId());
        AdminUserEntity assignee = adminUserMapper.selectById(ticket.getAssigneeUserId());
        return toResponse(ticket, exception, assignee);
    }

    private void validateStatusTransition(String current, String next) {
        if (current == null || current.equalsIgnoreCase(next)) {
            return;
        }
        if ("DONE".equalsIgnoreCase(current)) {
            throw new BusinessException("TICKET_ALREADY_DONE", "工单已完成");
        }
        if ("OPEN".equalsIgnoreCase(current) && !"IN_PROGRESS".equalsIgnoreCase(next) && !"DONE".equalsIgnoreCase(next)) {
            throw new BusinessException("TICKET_STATUS_INVALID", "OPEN 工单只能转为 IN_PROGRESS 或 DONE");
        }
        if ("IN_PROGRESS".equalsIgnoreCase(current) && !"DONE".equalsIgnoreCase(next)) {
            throw new BusinessException("TICKET_STATUS_INVALID", "处理中工单只能标记为 DONE");
        }
    }

    private void resolveLinkedException(Long exceptionId, String remark) {
        DispatchExceptionRecordEntity exception = exceptionRecordMapper.selectById(exceptionId);
        if (exception == null || "RESOLVED".equalsIgnoreCase(exception.getExceptionStatus())) {
            return;
        }
        DispatchExceptionResolveRequest request = new DispatchExceptionResolveRequest();
        request.setAction("CLOSE");
        request.setRemark(remark == null ? "现场工单已完成" : remark);
        request.setResolverId("FIELD_OPS");
        request.setResolverName("FIELD_OPS");
        dispatchExceptionService.resolveException(exceptionId, request);
    }

    private AdminFieldOpsTicketResponse toResponse(FieldOpsTicketEntity ticket,
                                                   DispatchExceptionRecordEntity exception,
                                                   AdminUserEntity assignee) {
        return AdminFieldOpsTicketResponse.builder()
                .id(ticket.getId())
                .exceptionId(ticket.getExceptionId())
                .assigneeUserId(ticket.getAssigneeUserId())
                .assigneeName(assignee == null ? null : assignee.getDisplayName())
                .status(ticket.getStatus())
                .notes(ticket.getNotes())
                .exceptionType(exception == null ? null : exception.getExceptionType())
                .exceptionMsg(exception == null ? null : exception.getExceptionMsg())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
