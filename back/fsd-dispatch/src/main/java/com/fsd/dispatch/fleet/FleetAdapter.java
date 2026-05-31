package com.fsd.dispatch.fleet;

import com.fsd.common.enums.VehicleLinkMode;

/** Marker for fleet telemetry adapters registered by link mode. */
public interface FleetAdapter {

    VehicleLinkMode supportedLinkMode();
}
