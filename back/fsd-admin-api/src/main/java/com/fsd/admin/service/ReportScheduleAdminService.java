package com.fsd.admin.service;

import com.fsd.admin.dto.AdminReportScheduleUpsertRequest;
import com.fsd.admin.vo.AdminReportScheduleResponse;
import java.util.List;

public interface ReportScheduleAdminService {

    List<AdminReportScheduleResponse> list();

    AdminReportScheduleResponse upsert(AdminReportScheduleUpsertRequest request);

    void delete(Long id);
}
