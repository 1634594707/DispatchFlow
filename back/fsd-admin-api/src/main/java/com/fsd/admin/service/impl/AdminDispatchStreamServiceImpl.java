package com.fsd.admin.service.impl;

import com.fsd.admin.config.AdminSseProperties;
import com.fsd.admin.service.AdminDispatchStreamService;
import com.fsd.common.exception.BusinessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AdminDispatchStreamServiceImpl implements AdminDispatchStreamService {

    private static final Logger log = LoggerFactory.getLogger(AdminDispatchStreamServiceImpl.class);

    private final AdminSseProperties properties;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AdminDispatchStreamServiceImpl(AdminSseProperties properties) {
        this.properties = properties;
    }

    @Override
    public SseEmitter createStream() {
        if (emitters.size() >= properties.getMaxConnections()) {
            throw new BusinessException("SSE_CONNECTION_LIMIT_EXCEEDED", "SSE 连接数已达上限");
        }
        SseEmitter emitter = new SseEmitter(properties.getTimeoutMs());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(e -> removeEmitter(emitter));

        return emitter;
    }

    @Override
    public void broadcast(String eventName, Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("Failed to send dispatch SSE event {}: {}", eventName, e.getMessage());
                deadEmitters.add(emitter);
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

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }
}
