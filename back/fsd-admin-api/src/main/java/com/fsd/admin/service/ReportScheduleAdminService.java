package com.fsd.admin.service;

import com.fsd.admin.dto.AdminReportScheduleUpsertRequest;
import com.fsd.admin.vo.AdminReportScheduleResponse;
import java.util.List;

public interface ReportScheduleAdminService {

    List<AdminReportScheduleResponse> list();

    default List<AdminReportScheduleResponse> list(Long parkId) {
        return list().stream()
                .filter(item -> parkId == null || parkId.equals(item.getParkId()))
                .toList();
    }

    AdminReportScheduleResponse upsert(AdminReportScheduleUpsertRequest request);

    default AdminReportScheduleResponse upsert(AdminReportScheduleUpsertRequest request, Long scopeParkId) {
        return upsert(request);
    }

    void delete(Long id);

    default void delete(Long id, Long scopeParkId) {
        delete(id);
    }
}
