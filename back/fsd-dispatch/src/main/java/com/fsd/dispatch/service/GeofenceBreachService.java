package com.fsd.dispatch.service;

import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;

public interface GeofenceBreachService {

    void evaluateVehiclePosition(Long parkId, VehicleEntity vehicle, BigDecimal longitude, BigDecimal latitude);

    /**
     * 检查车辆是否在允许的运行范围内
     * @param vehicleId 车辆ID
     * @param longitude 当前经度
     * @param latitude 当前纬度
     * @return true如果在允许范围内
     */
    boolean isWithinAllowedArea(Long vehicleId, BigDecimal longitude, BigDecimal latitude);
}
