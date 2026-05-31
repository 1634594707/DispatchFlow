package com.fsd.dispatch.service.impl;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.fleet.real.RealFleetAdapter;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import com.fsd.dispatch.service.VehicleGatewayService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleGatewayServiceImpl implements VehicleGatewayService {

    private final VehicleService vehicleService;
    private final VehicleReportService vehicleReportService;
    private final FleetAdapterRegistry fleetAdapterRegistry;
    private final VehicleTelemetryIdempotencyService telemetryIdempotencyService;

    public VehicleGatewayServiceImpl(VehicleService vehicleService,
                                     VehicleReportService vehicleReportService,
                                     FleetAdapterRegistry fleetAdapterRegistry,
                                     VehicleTelemetryIdempotencyService telemetryIdempotencyService) {
        this.vehicleService = vehicleService;
        this.vehicleReportService = vehicleReportService;
        this.fleetAdapterRegistry = fleetAdapterRegistry;
        this.telemetryIdempotencyService = telemetryIdempotencyService;
    }

    @Override
    @Transactional
    public void ingestTelemetry(VehicleTelemetryRequest request) {
        if (!telemetryIdempotencyService.markIfFirstTelemetry(request)) {
            return;
        }
        VehicleEntity vehicle = vehicleService.getByVehicleCode(request.getVehicleCode());
        assertRealVehicle(vehicle);
        RealFleetAdapter adapter = fleetAdapterRegistry.require(VehicleLinkMode.REAL, RealFleetAdapter.class);
        adapter.ingestTelemetry(vehicle, request);
        vehicle.setBatteryLevel(request.getSoc());
        vehicle.setCurrentLatitude(request.getY());
        vehicle.setCurrentLongitude(request.getX());
        vehicle.setLastReportTime(request.getReportTime());
        vehicleService.updateSnapshot(buildSnapshotRequest(vehicle, request));
    }

    @Override
    @Transactional
    public VehicleReportResponse handleReport(VehicleReportRequest request) {
        VehicleEntity vehicle = vehicleService.getByVehicleCode(request.getVehicleCode());
        assertRealVehicle(vehicle);
        return vehicleReportService.handleReport(request);
    }

    private void assertRealVehicle(VehicleEntity vehicle) {
        String linkMode = vehicle.getLinkMode() == null || vehicle.getLinkMode().isBlank()
                ? VehicleLinkMode.SIM.name()
                : vehicle.getLinkMode();
        if (!VehicleLinkMode.REAL.name().equals(linkMode)) {
            throw new BusinessException("VEHICLE_NOT_REAL", "Vehicle is not configured for real gateway access");
        }
    }

    private VehicleReportRequest buildSnapshotRequest(VehicleEntity vehicle, VehicleTelemetryRequest request) {
        VehicleReportRequest snapshot = new VehicleReportRequest();
        snapshot.setVehicleCode(vehicle.getVehicleCode());
        snapshot.setOnlineStatus(vehicle.getOnlineStatus());
        snapshot.setDispatchStatus(vehicle.getDispatchStatus());
        snapshot.setLatitude(request.getY());
        snapshot.setLongitude(request.getX());
        snapshot.setBatteryLevel(request.getSoc());
        snapshot.setReportTime(request.getReportTime());
        snapshot.setReportType("TELEMETRY");
        return snapshot;
    }
}
