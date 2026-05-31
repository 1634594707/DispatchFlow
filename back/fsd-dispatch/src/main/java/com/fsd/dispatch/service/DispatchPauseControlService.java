package com.fsd.dispatch.service;

public interface DispatchPauseControlService {

    boolean isDispatchPaused(Long parkId);

    void setDispatchPaused(Long parkId, boolean paused);

    boolean isGlobalDispatchPaused();
}
