package com.fsd.admin.service;

import com.fsd.admin.dto.AdminTrafficPauseZoneRequest;
import com.fsd.admin.vo.AdminTrafficPauseZoneResponse;
import com.fsd.admin.vo.AdminTrafficSegmentImpactResponse;
import com.fsd.admin.vo.AdminTrafficSegmentResponse;
import com.fsd.admin.vo.AdminTrafficSummaryResponse;
import java.util.List;

public interface TrafficAdminService {

    List<AdminTrafficSegmentResponse> getTrafficOverview(Long parkId);

    void refreshCongestion(Long parkId);

    AdminTrafficSummaryResponse getSummary(Long parkId);

    int countAffectedTasksForSegment(Long segmentId);

    AdminTrafficSegmentImpactResponse getSegmentImpact(Long segmentId);

    AdminTrafficSegmentResponse disableSegment(Long segmentId);

    AdminTrafficSegmentResponse downgradeCongestion(Long segmentId);

    List<AdminTrafficPauseZoneResponse> listPauseZones(Long parkId);

    AdminTrafficPauseZoneResponse addPauseZone(AdminTrafficPauseZoneRequest request);

    void clearPauseZones(Long parkId);
}
