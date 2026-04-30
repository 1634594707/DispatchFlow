package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchTaskStateService;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DispatchTaskStateServiceImpl implements DispatchTaskStateService {

    private final DispatchTaskMapper dispatchTaskMapper;

    public DispatchTaskStateServiceImpl(DispatchTaskMapper dispatchTaskMapper) {
        this.dispatchTaskMapper = dispatchTaskMapper;
    }

    @Override
    public DispatchTaskEntity getTask(Long taskId) {
        DispatchTaskEntity taskEntity = dispatchTaskMapper.selectById(taskId);
        if (taskEntity == null || Integer.valueOf(1).equals(taskEntity.getDeleted())) {
            throw new BusinessException("DISPATCH_TASK_NOT_FOUND", "Dispatch task not found");
        }
        return taskEntity;
    }

    @Override
    public void assertCanCreateTask(Long orderId) {
        Long count = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getOrderId, orderId)
                .eq(DispatchTaskEntity::getDeleted, 0));
        if (count != null && count > 0) {
            throw new BusinessException("DISPATCH_TASK_EXISTS", "Dispatch task already exists");
        }
    }

    @Override
    public void assertCanAutoAssign(DispatchTaskEntity taskEntity) {
        assertStatus(taskEntity, Set.of(DispatchTaskStatus.PENDING), "DISPATCH_TASK_STATUS_INVALID");
    }

    @Override
    public void assertCanManualAssign(DispatchTaskEntity taskEntity) {
        assertStatus(taskEntity, Set.of(DispatchTaskStatus.PENDING, DispatchTaskStatus.MANUAL_PENDING),
                "DISPATCH_TASK_STATUS_INVALID");
    }

    @Override
    public void assertCanStartExecute(DispatchTaskEntity taskEntity) {
        assertStatus(taskEntity, Set.of(DispatchTaskStatus.ASSIGNED), "DISPATCH_TASK_STATUS_INVALID");
    }

    @Override
    public void assertCanFinish(DispatchTaskEntity taskEntity) {
        assertStatus(taskEntity, Set.of(DispatchTaskStatus.ASSIGNED, DispatchTaskStatus.EXECUTING),
                "DISPATCH_TASK_STATUS_INVALID");
    }

    private void assertStatus(DispatchTaskEntity taskEntity, Set<DispatchTaskStatus> allowed, String errorCode) {
        DispatchTaskStatus current = DispatchTaskStatus.valueOf(taskEntity.getStatus());
        if (!allowed.contains(current)) {
            throw new BusinessException(errorCode, "Dispatch task status transition is not allowed");
        }
    }
}
