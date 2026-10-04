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
                // 先问有没有人看，再组装：listVehicleSnapshots 是 t_vehicle 全表读 + 每台车一次 Redis
                // 运行态读 + 轨迹组装，而 broadcast 的空订阅短路发生在这些开销已经付完之后。
                // 没人看大屏时（静置态占多数），这一句把整条链路的每秒成本降到零。
                if (!streamService.hasClients(park.getParkId())) {
                    continue;
                }
                List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots(park.getParkId());
                streamService.broadcast(park.getParkId(), vehicles);
            }
        } catch (Exception e) {
            log.debug("Failed to broadcast telemetry: {}", e.getMessage());
        }
    }
}
