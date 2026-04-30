package com.fsd.dispatch.event;

public interface DispatchEventConsumeIdempotencyService {

    boolean markIfFirstConsume(String eventId);
}
