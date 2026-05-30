package com.fsd.admin.service.impl;

import com.fsd.admin.service.VehicleTrajectoryAdminService;
import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VehicleTrajectoryAdminServiceImpl implements VehicleTrajectoryAdminService {

    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;

    public VehicleTrajectoryAdminServiceImpl(VehicleMapper vehicleMapper,
                                             FleetRuntimeService fleetRuntimeService) {
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
    }

    @Override
    public List<AdminTrajectoryPointResponse> getTrajectory(Long vehicleId) {
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || vehicle.getDeleted() != null && vehicle.getDeleted() != 0) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "车辆不存在");
        }
        List<AdminTrajectoryPointResponse> points = new ArrayList<>();
        FleetRuntime runtime = fleetRuntimeService.get(vehicleId).orElse(null);
        if (runtime != null && runtime.getTrajectory() != null) {
            for (FleetTrajectoryPoint point : runtime.getTrajectory()) {
                points.add(AdminTrajectoryPointResponse.builder()
                        .x(point.getX() == null ? null : point.getX().doubleValue())
                        .y(point.getY() == null ? null : point.getY().doubleValue())
                        .build());
            }
        }
        if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
            points.add(AdminTrajectoryPointResponse.builder()
                    .ts(vehicle.getLastReportTime())
                    .x(vehicle.getCurrentLongitude().doubleValue())
                    .y(vehicle.getCurrentLatitude().doubleValue())
                    .soc(vehicle.getBatteryLevel())
                    .build());
        }
        return points;
    }
}
