package com.fsd.common.enums;

public enum VehicleLinkMode {
    SIM,
    REAL,
    VDA5050;

    /** Vehicles that receive dispatch commands outside the simulation loop. */
    public static boolean issuesExternalCommands(String linkMode) {
        if (linkMode == null || linkMode.isBlank()) {
            return false;
        }
        return REAL.name().equals(linkMode) || VDA5050.name().equals(linkMode);
    }

    public static boolean isSimulated(String linkMode) {
        return !issuesExternalCommands(linkMode);
    }
}
