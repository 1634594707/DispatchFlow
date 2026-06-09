package com.fsd.admin.service.impl;

import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.common.exception.BusinessException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AdminDispatchStreamServiceImpl implements AdminDispatchStreamService {

    private static final Logger log = LoggerFactory.getLogger(AdminDispatchStreamServiceImpl.class);

    private final AdminSseProperties properties;
    private final CopyOnWriteArrayList<EmitterRegistration> emitters = new CopyOnWriteArrayList<>();

    public AdminDispatchStreamServiceImpl(AdminSseProperties properties) {
        this.properties = properties;
    }

    @Override
    public SseEmitter createStream() {
        return createStream(null, null);
    }

    @Override
    public SseEmitter createStream(AdminAuthContext context, Long parkId) {
        if (emitters.size() >= properties.getMaxConnections()) {
            throw new BusinessException("SSE_CONNECTION_LIMIT_EXCEEDED", "SSE 连接数已达上限");
        }
        SseEmitter emitter = new SseEmitter(properties.getTimeoutMs());
        EmitterRegistration registration = new EmitterRegistration(emitter, context, parkId);
        emitters.add(registration);

        emitter.onCompletion(() -> removeEmitter(registration));
        emitter.onTimeout(() -> removeEmitter(registration));
        emitter.onError(e -> removeEmitter(registration));

        return emitter;
    }

    @Override
    public void broadcast(String eventName, Object payload) {
        broadcast(eventName, payload, resolveParkId(payload));
    }

    @Override
    public void broadcast(String eventName, Object payload, Long parkId) {
        if (emitters.isEmpty()) {
            return;
        }
        List<EmitterRegistration> deadEmitters = new ArrayList<>();
        for (EmitterRegistration registration : emitters) {
            if (!registration.matchesPark(parkId)) {
                continue;
            }
            try {
                registration.emitter().send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("Failed to send dispatch SSE event {}: {}", eventName, e.getMessage());
                deadEmitters.add(registration);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    @Override
    public boolean hasClients() {
        return !emitters.isEmpty();
    }

    @Override
    public int getActiveConnectionCount() {
        return emitters.size();
    }

    private void removeEmitter(EmitterRegistration registration) {
        emitters.remove(registration);
    }

    void registerEmitterForTest(SseEmitter emitter, Long parkId) {
        emitters.add(new EmitterRegistration(emitter, null, parkId));
    }

    private Long resolveParkId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get("parkId");
            return value instanceof Number number ? number.longValue() : null;
        }
        if (payload == null) {
            return null;
        }
        try {
            Method method = payload.getClass().getMethod("getParkId");
            Object value = method.invoke(payload);
            return value instanceof Number number ? number.longValue() : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private record EmitterRegistration(SseEmitter emitter, AdminAuthContext context, Long parkId) {
        private boolean matchesPark(Long eventParkId) {
            return parkId == null || eventParkId == null || Objects.equals(parkId, eventParkId);
        }
    }
}
