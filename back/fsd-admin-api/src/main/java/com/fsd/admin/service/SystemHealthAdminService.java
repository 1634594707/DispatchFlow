package com.fsd.admin.service;

import com.fsd.admin.vo.AdminDetailedMetricsResponse;
import com.fsd.admin.vo.AdminSystemHealthResponse;

public interface SystemHealthAdminService {

    AdminSystemHealthResponse getHealth();

    AdminDetailedMetricsResponse getDetailedMetrics();
}
