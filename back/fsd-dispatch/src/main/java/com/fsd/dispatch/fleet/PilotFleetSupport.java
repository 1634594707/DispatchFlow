package com.fsd.dispatch.fleet;

import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.vehicle.entity.VehicleEntity;

/** 园区示意仿真（PARK-*）与叠石桥地理仿真（ZJF-AV-*）分池，避免混派与状态冲突。 */
public final class PilotFleetSupport {

    public static final String SCHEMATIC_VEHICLE_PREFIX = "PARK-";
    public static final String GEO_VEHICLE_PREFIX = "ZJF-AV-";

    private PilotFleetSupport() {
    }

    public static boolean isPilotSimVehicleCode(String vehicleCode) {
        return isSchematicPilotVehicleCode(vehicleCode) || isGeoPilotVehicleCode(vehicleCode);
    }

    public static boolean isSchematicPilotVehicleCode(String vehicleCode) {
        return vehicleCode != null && vehicleCode.startsWith(SCHEMATIC_VEHICLE_PREFIX);
    }

    public static boolean isGeoPilotVehicleCode(String vehicleCode) {
        return vehicleCode != null && vehicleCode.startsWith(GEO_VEHICLE_PREFIX);
    }

    public static boolean isSchematicPilotVehicle(VehicleEntity vehicle) {
        return vehicle != null && isSchematicPilotVehicleCode(vehicle.getVehicleCode());
    }

    public static boolean isGeoPilotVehicle(VehicleEntity vehicle) {
        return vehicle != null && isGeoPilotVehicleCode(vehicle.getVehicleCode());
    }

    public static boolean isGeoDeliveryStation(ParkStationResponse station) {
        if (station == null) {
            return false;
        }
        if ("ZJF".equals(station.getArea())) {
            return true;
        }
        String code = station.getStationCode();
        return code != null && code.startsWith("ZJF-");
    }

    public static boolean isSchematicDeliveryStation(ParkStationResponse station) {
        return station != null && !isGeoDeliveryStation(station);
    }

    public static boolean isGeoDeliveryOrder(OrderEntity order, ParkStationResponse pickup, ParkStationResponse dropoff) {
        if (order != null && order.getParkId() != null) {
            // 移动下单 / ZJF 站点点位优先按站点区域判定
        }
        return isGeoDeliveryStation(pickup) || isGeoDeliveryStation(dropoff);
    }

    public static boolean matchesOrderFleet(VehicleEntity vehicle,
                                            ParkStationResponse pickup,
                                            ParkStationResponse dropoff) {
        if (vehicle == null || vehicle.getVehicleCode() == null) {
            return false;
        }
        boolean geoOrder = isGeoDeliveryStation(pickup) || isGeoDeliveryStation(dropoff);
        if (geoOrder) {
            return isGeoPilotVehicle(vehicle);
        }
        return isSchematicPilotVehicle(vehicle);
    }
}
