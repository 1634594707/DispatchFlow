package com.fsd.dispatch.service.impl;

import com.fsd.dispatch.entity.FleetTelemetryPointEntity;
import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import com.fsd.dispatch.mapper.FleetTelemetryPointMapper;
import com.fsd.dispatch.service.FleetTelemetryPersistenceService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class FleetTelemetryPersistenceServiceImpl implements FleetTelemetryPersistenceService {

    private final FleetTelemetryPointMapper telemetryPointMapper;

    public FleetTelemetryPersistenceServiceImpl(FleetTelemetryPointMapper telemetryPointMapper) {
        this.telemetryPointMapper = telemetryPointMapper;
    }

    @Override
    public void persistPoint(Long vehicleId, Long parkId, BigDecimal x, BigDecimal y, Integer soc,
                             LocalDateTime recordedAt) {
        if (vehicleId == null || x == null || y == null) {
            return;
        }
        FleetTelemetryPointEntity entity = new FleetTelemetryPointEntity();
        entity.setVehicleId(vehicleId);
        entity.setParkId(parkId);
        entity.setCoordX(x);
        entity.setCoordY(y);
        entity.setSoc(soc);
        entity.setRecordedAt(recordedAt == null ? LocalDateTime.now() : recordedAt);
        entity.setCreatedAt(LocalDateTime.now());
        telemetryPointMapper.insert(entity);
    }

    @Override
    public void persistFromTrajectory(Long vehicleId, Long parkId, FleetTrajectoryPoint point,
                                      LocalDateTime recordedAt) {
        if (point == null || point.getX() == null || point.getY() == null) {
            return;
        }
        persistPoint(vehicleId, parkId, point.getX(), point.getY(), null, recordedAt);
    }
}
