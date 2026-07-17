package com.fsd.dispatch.service;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.entity.DispatchTaskEntity;

public interface DispatchTaskStateService {

    DispatchTaskEntity getTask(Long taskId);

    void assertCanCreateTask(Long orderId);

    void assertCanAutoAssign(DispatchTaskEntity taskEntity);

    void assertCanManualAssign(DispatchTaskEntity taskEntity);

    void assertCanStartExecute(DispatchTaskEntity taskEntity);

    void assertCanFinish(DispatchTaskEntity taskEntity);

    void assertCanCancel(DispatchTaskEntity taskEntity);

    void assertCanReassign(DispatchTaskEntity taskEntity);

    /** 校验任务从当前状态切换到目标状态是否被允许（基于状态机规则）。 */
    void assertCanTransition(DispatchTaskEntity taskEntity, DispatchTaskStatus targetStatus);
}
