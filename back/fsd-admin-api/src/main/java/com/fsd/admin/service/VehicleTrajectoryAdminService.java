package com.fsd.admin.service;

import com.fsd.admin.vo.AdminTrajectoryDwellResponse;
import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import com.fsd.admin.util.TrajectoryDwellDetector;
import java.time.LocalDateTime;
import java.util.List;

public interface VehicleTrajectoryAdminService {

    List<AdminTrajectoryPointResponse> getTrajectory(Long vehicleId, LocalDateTime from, LocalDateTime to, String source);

    List<AdminTrajectoryDwellResponse> getDwellPoints(Long vehicleId, LocalDateTime from, LocalDateTime to);
}
