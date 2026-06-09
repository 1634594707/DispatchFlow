package com.fsd.admin.service;

import com.fsd.admin.auth.AdminAuthContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AdminDispatchStreamService {

    SseEmitter createStream();

    SseEmitter createStream(AdminAuthContext context, Long parkId);

    void broadcast(String eventName, Object payload);

    void broadcast(String eventName, Object payload, Long parkId);

    boolean hasClients();

    int getActiveConnectionCount();
}
