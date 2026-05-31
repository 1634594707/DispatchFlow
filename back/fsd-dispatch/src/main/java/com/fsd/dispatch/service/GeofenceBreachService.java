package com.fsd.dispatch.service;

import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;

public interface GeofenceBreachService {

    void evaluateVehiclePosition(Long parkId, VehicleEntity vehicle, BigDecimal longitude, BigDecimal latitude);
}
