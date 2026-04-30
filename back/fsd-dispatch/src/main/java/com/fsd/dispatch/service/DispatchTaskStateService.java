package com.fsd.dispatch.service;

import com.fsd.dispatch.entity.DispatchTaskEntity;

public interface DispatchTaskStateService {

    DispatchTaskEntity getTask(Long taskId);

    void assertCanCreateTask(Long orderId);

    void assertCanAutoAssign(DispatchTaskEntity taskEntity);

    void assertCanManualAssign(DispatchTaskEntity taskEntity);

    void assertCanStartExecute(DispatchTaskEntity taskEntity);

    void assertCanFinish(DispatchTaskEntity taskEntity);
}
