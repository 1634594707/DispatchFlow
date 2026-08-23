package com.fsd.admin.service;

import com.fsd.admin.vo.AdminGlobalSearchResponse;

public interface GlobalSearchAdminService {

    AdminGlobalSearchResponse search(String keyword, int limit);

    default AdminGlobalSearchResponse search(String keyword, int limit, Long parkId) {
        return search(keyword, limit);
    }
}
