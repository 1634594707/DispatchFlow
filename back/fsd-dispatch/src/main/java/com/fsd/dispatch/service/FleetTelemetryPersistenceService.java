package com.fsd.dispatch.service;

import com.fsd.dispatch.fleet.model.FleetTrajectoryPoint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface FleetTelemetryPersistenceService {

    void persistPoint(Long vehicleId, Long parkId, BigDecimal x, BigDecimal y, Integer soc, LocalDateTime recordedAt);

    void persistFromTrajectory(Long vehicleId, Long parkId, FleetTrajectoryPoint point, LocalDateTime recordedAt);
}
