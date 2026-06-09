package com.fsd.dispatch.fleet.vda5050;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.enums.VehicleOnlineStatus;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Vda5050StateIngestService {

    private static final Logger log = LoggerFactory.getLogger(Vda5050StateIngestService.class);

    private final ObjectMapper objectMapper;
    private final Vda5050VehicleRegistry vehicleRegistry;
    private final FleetAdapterRegistry fleetAdapterRegistry;
    private final VehicleService vehicleService;

    public Vda5050StateIngestService(ObjectMapper objectMapper,
                                     Vda5050VehicleRegistry vehicleRegistry,
                                     FleetAdapterRegistry fleetAdapterRegistry,
                                     VehicleService vehicleService) {
        this.objectMapper = objectMapper;
        this.vehicleRegistry = vehicleRegistry;
        this.fleetAdapterRegistry = fleetAdapterRegistry;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public void ingestStateTopic(String topic, String payload) {
        Vda5050TopicHelper.TopicIdentity identity = Vda5050TopicHelper.parseTopic(topic);
        if (identity == null || !"state".equals(identity.channel())) {
            return;
        }
        vehicleRegistry.findByIdentity(identity.manufacturer(), identity.serialNumber())
                .ifPresent(vehicle -> ingestState(vehicle, payload));
    }

    @Transactional
    public void ingestState(VehicleEntity vehicle, String payload) {
        if (!VehicleLinkMode.VDA5050.name().equals(vehicle.getLinkMode())) {
            return;
        }
        try {
            JsonNode state = objectMapper.readTree(payload);
            VehicleTelemetryRequest request = Vda5050StateMapper.toTelemetry(vehicle, state, System.currentTimeMillis());
            Vda5050FleetAdapter adapter = fleetAdapterRegistry.require(VehicleLinkMode.VDA5050, Vda5050FleetAdapter.class);
            adapter.ingestTelemetry(vehicle, request);
            VehicleReportRequest snapshot = new VehicleReportRequest();
            snapshot.setVehicleCode(vehicle.getVehicleCode());
            snapshot.setOnlineStatus(VehicleOnlineStatus.ONLINE.name());
            snapshot.setDispatchStatus(vehicle.getDispatchStatus());
            snapshot.setLatitude(request.getY());
            snapshot.setLongitude(request.getX());
            snapshot.setBatteryLevel(request.getSoc());
            snapshot.setReportTime(request.getReportTime());
            snapshot.setReportType("VDA5050_STATE");
            vehicleService.updateSnapshot(snapshot);
        } catch (java.io.IOException ex) {
            log.warn("Failed to ingest VDA5050 state for {}: {}", vehicle.getVehicleCode(), ex.getMessage());
        }
    }
}
