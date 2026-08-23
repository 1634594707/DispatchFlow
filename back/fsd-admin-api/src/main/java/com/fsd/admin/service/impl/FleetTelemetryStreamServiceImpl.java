package com.fsd.admin.service.impl;

import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.service.FleetTelemetryStreamService;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 车辆监控实时流（路线图 4.1：与调度工作台流统一认证、心跳、超时与上限策略）。
 *
 * <ul>
 *   <li>认证：一次性 ticket（Controller 层消费，Redis 跨实例）；</li>
 *   <li>超时：与调度流共用 fsd.admin.sse.timeout-ms；</li>
 *   <li>连接上限：共用 fsd.admin.sse.max-connections；</li>
 *   <li>心跳：每 30s 下发 ping 注释帧，防止代理断开空闲连接并支持客户端判活；</li>
 *   <li>指标：独立 telemetry 前缀，避免与调度流混淆。</li>
 * </ul>
 */
@Service
public class FleetTelemetryStreamServiceImpl implements FleetTelemetryStreamService {

    private static final Logger log = LoggerFactory.getLogger(FleetTelemetryStreamServiceImpl.class);

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Counter opened;
    private final Counter closed;
    private final Counter rejected;
    private final AdminSseProperties properties;

    public FleetTelemetryStreamServiceImpl(AdminSseProperties properties, MeterRegistry registry) {
        this.properties = properties;
        Gauge.builder("dispatchflow.sse.telemetry.connections.active", activeConnections, AtomicInteger::get)
                .description("Active fleet telemetry SSE connections")
                .register(registry);
        opened = Counter.builder("dispatchflow.sse.telemetry.connections.opened")
                .description("Fleet telemetry SSE connections opened")
                .register(registry);
        closed = Counter.builder("dispatchflow.sse.telemetry.connections.closed")
                .description("Fleet telemetry SSE connections closed")
                .register(registry);
        rejected = Counter.builder("dispatchflow.sse.telemetry.connections.rejected")
                .description("Fleet telemetry SSE connection attempts rejected")
                .register(registry);
    }

    @Override
    public SseEmitter createStream(Long parkId) {
        if (activeConnections.get() >= properties.getMaxConnections()) {
            rejected.increment();
            throw new com.fsd.common.exception.BusinessException("SSE_CONNECTION_LIMIT_EXCEEDED",
                    "车辆监控连接数已达上限");
        }
        // 统一超时策略：不再使用永不超时连接
        SseEmitter emitter = new SseEmitter(properties.getTimeoutMs());
        Long key = parkId != null ? parkId : 0L;

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        activeConnections.incrementAndGet();
        opened.increment();

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

    /** 测试专用：绕过上限直接注册发射器。 */
    void registerEmitterForTest(SseEmitter emitter, Long parkId) {
        Long key = parkId != null ? parkId : 0L;
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        activeConnections.incrementAndGet();
        opened.increment();
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
                "ts", Instant.now().toString(),
                "vehicles", vehicles
        );

        List<SseEmitter> deadEmitters = new ArrayList<>();

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

    /** 统一心跳：30s 注释帧保活（路线图 4.1）。 */
    @Scheduled(fixedDelay = 30000L)
    public void heartbeat() {
        List<Long> keys = new ArrayList<>(emitters.keySet());
        for (Long key : keys) {
            CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(key);
            if (emitterList == null) {
                continue;
            }
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            for (SseEmitter dead : deadEmitters) {
                removeEmitter(key, dead);
                try {
                    dead.complete();
                } catch (Exception ignored) {
                    // 已断连发射器 complete 可能再抛错，忽略
                }
            }
        }
    }

    public int getActiveConnectionCount() {
        return activeConnections.get();
    }

    private void removeEmitter(Long key, SseEmitter emitter) {
        // completion/timeout/error 可能连续触发：只在真正移除时记账一次
        boolean removed = false;
        CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(key);
        if (emitterList != null) {
            removed = emitterList.remove(emitter);
            if (removed && emitterList.isEmpty()) {
                emitters.remove(key);
            }
        }
        if (removed) {
            activeConnections.updateAndGet(value -> Math.max(0, value - 1));
            closed.increment();
        }
    }

    @Scheduled(fixedDelay = 300000L)
    public void cleanupStaleEmitters() {
        emitters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}