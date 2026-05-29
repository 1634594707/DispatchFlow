package com.fsd.dispatch.controller;

public final class VehicleGatewayAuthContext {

    private static final ThreadLocal<String> VEHICLE_CODE = new ThreadLocal<>();

    private VehicleGatewayAuthContext() {
    }

    public static void setVehicleCode(String vehicleCode) {
        VEHICLE_CODE.set(vehicleCode);
    }

    public static String getVehicleCode() {
        return VEHICLE_CODE.get();
    }

    public static void clear() {
        VEHICLE_CODE.remove();
    }
}
