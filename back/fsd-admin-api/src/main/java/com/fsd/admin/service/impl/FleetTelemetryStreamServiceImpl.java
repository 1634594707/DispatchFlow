package com.fsd.admin.service.impl;

import com.fsd.admin.service.FleetTelemetryStreamService;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class FleetTelemetryStreamServiceImpl implements FleetTelemetryStreamService {

    private static final Logger log = LoggerFactory.getLogger(FleetTelemetryStreamServiceImpl.class);
    private static final Long DEFAULT_TIMEOUT = 0L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter createStream(Long parkId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        Long key = parkId != null ? parkId : 0L;

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for park {}", key);
            removeEmitter(key, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for park {}", key);
            removeEmitter(key, emitter);
        });

        emitter.onError(e -> {
            log.debug("SSE emitter error for park {}: {}", key, e.getMessage());
            removeEmitter(key, emitter);
        });

        return emitter;
    }

    @Override
    public void broadcast(Long parkId, List<ParkVehicleSnapshotResponse> vehicles) {
        Long key = parkId != null ? parkId : 0L;
        CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(key);

        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "parkId", key,
                "ts", java.time.Instant.now().toString(),
                "vehicles", vehicles
        );

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event()
                        .name("telemetry")
                        .data(payload));
            } catch (IOException e) {
                log.debug("Failed to send SSE event: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        emitterList.removeAll(deadEmitters);
    }

    private void removeEmitter(Long key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(key);
        if (emitterList != null) {
            emitterList.remove(emitter);
            if (emitterList.isEmpty()) {
                emitters.remove(key);
            }
        }
    }
}
