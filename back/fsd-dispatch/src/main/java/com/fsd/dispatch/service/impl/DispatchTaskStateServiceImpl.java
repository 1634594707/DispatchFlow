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

    // 状态迁移表唯一来源：com.fsd.common.enums.DispatchTaskStatus#ALLOWED_TRANSITIONS（路线图 2.2）。

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
        // MANUAL_PENDING：自动派单失败后重试；ASSIGNING：中断后允许再次触发
        assertStatus(taskEntity,
                Set.of(DispatchTaskStatus.PENDING, DispatchTaskStatus.MANUAL_PENDING, DispatchTaskStatus.ASSIGNING),
                "DISPATCH_TASK_STATUS_INVALID");
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

    @Override
    public void assertCanCancel(DispatchTaskEntity taskEntity) {
        // ASSIGNING 阶段也允许取消（补充缺失的转换路径 ASSIGNING -> CANCELLED）
        assertStatus(taskEntity,
                Set.of(DispatchTaskStatus.PENDING, DispatchTaskStatus.MANUAL_PENDING,
                        DispatchTaskStatus.ASSIGNING,
                        DispatchTaskStatus.ASSIGNED, DispatchTaskStatus.EXECUTING),
                "DISPATCH_TASK_STATUS_INVALID");
    }

    @Override
    public void assertCanReassign(DispatchTaskEntity taskEntity) {
        assertStatus(taskEntity, Set.of(DispatchTaskStatus.ASSIGNED), "DISPATCH_TASK_STATUS_INVALID");
    }

    @Override
    public void assertCanTransition(DispatchTaskEntity taskEntity, DispatchTaskStatus targetStatus) {
        DispatchTaskStatus current = resolveStatus(taskEntity);
        Set<DispatchTaskStatus> allowed = current.allowedNextStatuses();
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException("DISPATCH_TASK_STATUS_INVALID",
                    "任务状态「" + current.name() + "」不允许流转到「" + targetStatus.name() + "」，允许的目标状态: "
                            + allowed.stream().map(Enum::name).sorted().reduce((a, b) -> a + "/" + b).orElse("(终态)"));
        }
    }

    private void assertStatus(DispatchTaskEntity taskEntity, Set<DispatchTaskStatus> allowed, String errorCode) {
        DispatchTaskStatus current = resolveStatus(taskEntity);
        if (!allowed.contains(current)) {
            throw new BusinessException(errorCode,
                    "任务状态「" + current.name() + "」不允许此操作，允许的状态: "
                            + allowed.stream().map(Enum::name).reduce((a, b) -> a + "/" + b).orElse(""));
        }
    }

    private DispatchTaskStatus resolveStatus(DispatchTaskEntity taskEntity) {
        try {
            return DispatchTaskStatus.valueOf(taskEntity.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("DISPATCH_TASK_STATUS_INVALID",
                    "未知任务状态: " + taskEntity.getStatus());
        }
    }
}
