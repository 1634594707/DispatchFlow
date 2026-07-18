package com.fsd.dispatch.fleet.vda5050;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapter;
import com.fsd.dispatch.fleet.real.RealFleetAdapter;
import com.fsd.vehicle.entity.VehicleEntity;
import org.springframework.stereotype.Component;

@Component
public class Vda5050FleetAdapter implements FleetAdapter {

    private final RealFleetAdapter realFleetAdapter;

    public Vda5050FleetAdapter(RealFleetAdapter realFleetAdapter) {
        this.realFleetAdapter = realFleetAdapter;
    }

    @Override
    public VehicleLinkMode supportedLinkMode() {
        return VehicleLinkMode.VDA5050;
    }

    public boolean ingestTelemetry(VehicleEntity vehicle, VehicleTelemetryRequest request) {
        return realFleetAdapter.ingestTelemetry(vehicle, request);
    }
}
