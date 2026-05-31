package com.fsd.admin.service.impl;

import com.fsd.admin.dto.AdminReportScheduleUpsertRequest;
import com.fsd.admin.service.ReportScheduleAdminService;
import com.fsd.admin.vo.AdminReportScheduleResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ReportScheduleEntity;
import com.fsd.dispatch.mapper.ReportScheduleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportScheduleAdminServiceImpl implements ReportScheduleAdminService {

    private final ReportScheduleMapper reportScheduleMapper;

    public ReportScheduleAdminServiceImpl(ReportScheduleMapper reportScheduleMapper) {
        this.reportScheduleMapper = reportScheduleMapper;
    }

    @Override
    public List<AdminReportScheduleResponse> list() {
        return reportScheduleMapper.selectList(null).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AdminReportScheduleResponse upsert(AdminReportScheduleUpsertRequest request) {
        ReportScheduleEntity entity;
        if (request.getId() == null) {
            entity = new ReportScheduleEntity();
            entity.setCreatedAt(LocalDateTime.now());
        } else {
            entity = reportScheduleMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException("SCHEDULE_NOT_FOUND", "定时任务不存在");
            }
        }
        entity.setParkId(request.getParkId());
        entity.setCronExpression(request.getCronExpression());
        entity.setRecipients(request.getRecipients());
        entity.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        entity.setUpdatedAt(LocalDateTime.now());
        if (request.getId() == null) {
            reportScheduleMapper.insert(entity);
        } else {
            reportScheduleMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        reportScheduleMapper.deleteById(id);
    }

    private AdminReportScheduleResponse toResponse(ReportScheduleEntity entity) {
        return AdminReportScheduleResponse.builder()
                .id(entity.getId())
                .parkId(entity.getParkId())
                .cronExpression(entity.getCronExpression())
                .recipients(entity.getRecipients())
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .lastSentAt(entity.getLastSentAt())
                .build();
    }
}
