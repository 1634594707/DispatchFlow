package com.fsd.admin.service;

import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FleetTelemetryStreamService {

    SseEmitter createStream(Long parkId);

    void broadcast(Long parkId, List<ParkVehicleSnapshotResponse> vehicles);
}
