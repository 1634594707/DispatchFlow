package com.fsd.admin.service;

import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import java.util.List;

public interface TrafficAdminService {

    List<AdminTrafficSegmentResponse> getTrafficOverview(Long parkId);

    void refreshCongestion(Long parkId);
}
