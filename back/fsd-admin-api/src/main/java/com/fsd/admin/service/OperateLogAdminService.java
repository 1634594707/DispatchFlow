package com.fsd.admin.service;

import com.fsd.admin.dto.AdminOperateLogQueryRequest;
import com.fsd.admin.vo.AdminOperateLogResponse;
import com.fsd.common.model.PageResponse;
import java.util.List;

public interface OperateLogAdminService {

    PageResponse<AdminOperateLogResponse> queryLogs(AdminOperateLogQueryRequest request);

    List<AdminOperateLogResponse> listByTaskId(Long taskId);

    default List<AdminOperateLogResponse> listByTaskId(Long taskId, Long parkId) {
        return listByTaskId(taskId);
    }

    List<AdminOperateLogResponse> listByVehicleId(Long vehicleId);

    default List<AdminOperateLogResponse> listByVehicleId(Long vehicleId, Long parkId) {
        return listByVehicleId(vehicleId);
    }

    String exportCsv(AdminOperateLogQueryRequest request);
}
