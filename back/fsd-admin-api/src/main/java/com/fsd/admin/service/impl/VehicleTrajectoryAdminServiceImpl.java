package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.VehicleTrajectoryAdminService;
import com.fsd.admin.vo.AdminTrajectoryDwellResponse;
import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import com.fsd.admin.util.TrajectoryDwellDetector;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.FleetTelemetryPointEntity;
import com.fsd.dispatch.fleet.model.FleetRuntime;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.FleetTelemetryPointMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VehicleTrajectoryAdminServiceImpl implements VehicleTrajectoryAdminService {

    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final FleetTelemetryPointMapper telemetryPointMapper;

    public VehicleTrajectoryAdminServiceImpl(VehicleMapper vehicleMapper,
                                             FleetRuntimeService fleetRuntimeService,
                                             FleetTelemetryPointMapper telemetryPointMapper) {
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.telemetryPointMapper = telemetryPointMapper;
    }

    @Override
    public List<AdminTrajectoryPointResponse> getTrajectory(Long vehicleId, LocalDateTime from,
                                                            LocalDateTime to, String source) {
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || vehicle.getDeleted() != null && vehicle.getDeleted() != 0) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "车辆不存在");
        }
        boolean history = "history".equalsIgnoreCase(source) || from != null || to != null;
        if (history) {
            return loadHistory(vehicleId, from, to);
        }
        return loadRealtime(vehicle);
    }

    private List<AdminTrajectoryPointResponse> loadHistory(Long vehicleId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusHours(24) : from;
        List<FleetTelemetryPointEntity> rows = telemetryPointMapper.selectList(
                new LambdaQueryWrapper<FleetTelemetryPointEntity>()
                        .eq(FleetTelemetryPointEntity::getVehicleId, vehicleId)
                        .ge(FleetTelemetryPointEntity::getRecordedAt, start)
                        .le(FleetTelemetryPointEntity::getRecordedAt, end)
                        .orderByAsc(FleetTelemetryPointEntity::getRecordedAt));
        List<AdminTrajectoryPointResponse> points = new ArrayList<>();
        for (FleetTelemetryPointEntity row : rows) {
            points.add(AdminTrajectoryPointResponse.builder()
                    .ts(row.getRecordedAt())
                    .x(row.getCoordX() == null ? null : row.getCoordX().doubleValue())
                    .y(row.getCoordY() == null ? null : row.getCoordY().doubleValue())
                    .soc(row.getSoc())
                    .build());
        }
        return points;
    }

    @Override
    public List<AdminTrajectoryDwellResponse> getDwellPoints(Long vehicleId, LocalDateTime from, LocalDateTime to) {
        return TrajectoryDwellDetector.detect(getTrajectory(vehicleId, from, to, "history"));
    }

    private List<AdminTrajectoryPointResponse> loadRealtime(VehicleEntity vehicle) {
        List<AdminTrajectoryPointResponse> points = new ArrayList<>();
        FleetRuntime runtime = fleetRuntimeService.get(vehicle.getId()).orElse(null);
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
