package com.fsd.admin.service;

import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import java.util.List;

public interface VehicleTrajectoryAdminService {

    List<AdminTrajectoryPointResponse> getTrajectory(Long vehicleId);
}
