package com.fsd.admin.service;

import com.fsd.admin.dto.AdminDispatchStrategyUpsertRequest;
import com.fsd.admin.vo.AdminDispatchStrategyResponse;
import com.fsd.admin.vo.AdminStrategyChangeLogResponse;
import java.util.List;

public interface DispatchStrategyAdminService {

    List<AdminDispatchStrategyResponse> listProfiles();

    List<AdminStrategyChangeLogResponse> listChangeLogs();

    AdminDispatchStrategyResponse create(AdminDispatchStrategyUpsertRequest request, String operatorName);

    AdminDispatchStrategyResponse update(Long id, AdminDispatchStrategyUpsertRequest request, String operatorName);

    void activate(Long id, String operatorName);
}
