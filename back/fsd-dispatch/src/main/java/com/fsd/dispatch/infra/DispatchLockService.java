package com.fsd.dispatch.infra;

public interface DispatchLockService {

    String acquireTaskLock(Long taskId);

    void releaseTaskLock(Long taskId, String lockToken);
}
