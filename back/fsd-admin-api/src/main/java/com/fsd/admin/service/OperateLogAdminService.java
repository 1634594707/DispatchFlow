package com.fsd.admin.service;

import com.fsd.admin.dto.AdminOperateLogQueryRequest;
import com.fsd.admin.vo.AdminOperateLogResponse;
import com.fsd.common.model.PageResponse;
import java.util.List;

public interface OperateLogAdminService {

    PageResponse<AdminOperateLogResponse> queryLogs(AdminOperateLogQueryRequest request);

    List<AdminOperateLogResponse> listByTaskId(Long taskId);

    List<AdminOperateLogResponse> listByVehicleId(Long vehicleId);

    String exportCsv(AdminOperateLogQueryRequest request);
}
