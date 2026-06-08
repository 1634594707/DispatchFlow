package com.fsd.admin.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AdminDispatchStreamService {

    SseEmitter createStream();

    void broadcast(String eventName, Object payload);

    boolean hasClients();

    int getActiveConnectionCount();
}
