package com.fsd.admin.service;

import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "fsd.fleet.telemetry.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class FleetTelemetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FleetTelemetryScheduler.class);

    private final FleetTelemetryStreamService streamService;
    private final ParkPilotService parkPilotService;

    public FleetTelemetryScheduler(FleetTelemetryStreamService streamService,
                                   ParkPilotService parkPilotService) {
        this.streamService = streamService;
        this.parkPilotService = parkPilotService;
    }

    @Scheduled(fixedDelay = 1000)
    public void broadcastTelemetry() {
        try {
            List<ParkResponse> parks = parkPilotService.listParks();
            for (ParkResponse park : parks) {
                List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots();
                streamService.broadcast(park.getParkId(), vehicles);
            }
        } catch (Exception e) {
            log.debug("Failed to broadcast telemetry: {}", e.getMessage());
        }
    }
}
