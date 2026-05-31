package com.fsd.admin.service;

import com.fsd.admin.vo.AdminReportScheduleResponse;
import java.util.List;

public interface ReportScheduleAdminService {

    List<AdminReportScheduleResponse> list();

    AdminReportScheduleResponse upsert(AdminReportScheduleResponse request);

    void delete(Long id);
}
