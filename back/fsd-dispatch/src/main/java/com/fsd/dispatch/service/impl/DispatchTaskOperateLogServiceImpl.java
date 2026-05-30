package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchTaskOperateLogEntity;
import com.fsd.dispatch.mapper.DispatchTaskOperateLogMapper;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchTaskOperateLogServiceImpl implements DispatchTaskOperateLogService {

    private final DispatchTaskOperateLogMapper operateLogMapper;

    public DispatchTaskOperateLogServiceImpl(DispatchTaskOperateLogMapper operateLogMapper) {
        this.operateLogMapper = operateLogMapper;
    }

    @Override
    @Transactional
    public void record(Long taskId, String operateType, String beforeStatus, String afterStatus,
                       String operatorType, String operatorId, String operatorName, String remark) {
        DispatchTaskOperateLogEntity entity = new DispatchTaskOperateLogEntity();
        entity.setTaskId(taskId);
        entity.setOperateType(operateType);
        entity.setBeforeStatus(beforeStatus);
        entity.setAfterStatus(afterStatus);
        entity.setOperatorType(operatorType);
        entity.setOperatorId(operatorId);
        entity.setOperatorName(operatorName);
        entity.setOperateRemark(remark);
        entity.setCreatedAt(LocalDateTime.now());
        operateLogMapper.insert(entity);
    }

    @Override
    public List<DispatchTaskOperateLogEntity> listByTaskId(Long taskId) {
        return operateLogMapper.selectList(new LambdaQueryWrapper<DispatchTaskOperateLogEntity>()
                .eq(DispatchTaskOperateLogEntity::getTaskId, taskId)
                .orderByAsc(DispatchTaskOperateLogEntity::getCreatedAt));
    }

    @Override
    public List<DispatchTaskOperateLogEntity> listByTaskIds(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return operateLogMapper.selectList(new LambdaQueryWrapper<DispatchTaskOperateLogEntity>()
                .in(DispatchTaskOperateLogEntity::getTaskId, taskIds)
                .orderByDesc(DispatchTaskOperateLogEntity::getCreatedAt));
    }
}
