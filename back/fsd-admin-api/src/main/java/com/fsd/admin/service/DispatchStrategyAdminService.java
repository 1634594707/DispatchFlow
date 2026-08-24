package com.fsd.admin.service;

import com.fsd.admin.dto.AdminDispatchStrategyUpsertRequest;
import com.fsd.admin.vo.AdminDispatchStrategyResponse;
import com.fsd.admin.vo.AdminStrategyChangeLogResponse;
import java.util.List;

public interface DispatchStrategyAdminService {

    List<AdminDispatchStrategyResponse> listProfiles();

    /** 按园区过滤（命中园区专属或全局模板）；parkId 为空返回全部（路线图 2.1 作用域登记）。 */
    default List<AdminDispatchStrategyResponse> listProfiles(Long parkId) {
        return listProfiles();
    }

    List<AdminStrategyChangeLogResponse> listChangeLogs();

    AdminDispatchStrategyResponse create(AdminDispatchStrategyUpsertRequest request, String operatorName);

    AdminDispatchStrategyResponse update(Long id, AdminDispatchStrategyUpsertRequest request, String operatorName);

    void activate(Long id, String operatorName);
}
