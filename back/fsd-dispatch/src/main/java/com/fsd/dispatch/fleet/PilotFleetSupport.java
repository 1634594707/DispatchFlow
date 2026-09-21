package com.fsd.dispatch.fleet;

import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;

/**
 * 叠石桥地理仿真车队（{@code ZJF-AV-*}）判定。
 *
 * <p>园区示意池（{@code PARK-*}）已随 §7.6 一并删除：没有真车时地理池是地图上唯一的动力源，
 * 双池并存只会让派单出现"GEO / 示意"两条路径，而示意那条早已没有数据。
 */
public final class PilotFleetSupport {

    public static final String GEO_VEHICLE_PREFIX = "ZJF-AV-";

    private PilotFleetSupport() {
    }

    public static boolean isPilotSimVehicleCode(String vehicleCode) {
        return isGeoPilotVehicleCode(vehicleCode);
    }

    public static boolean isGeoPilotVehicleCode(String vehicleCode) {
        return vehicleCode != null && vehicleCode.startsWith(GEO_VEHICLE_PREFIX);
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

    /** 只有地理池可派单：示意池已删，历史遗留的 {@code PARK-*} 车不再参与撮合。 */
    public static boolean matchesOrderFleet(VehicleEntity vehicle) {
        return isGeoPilotVehicle(vehicle);
    }
}
